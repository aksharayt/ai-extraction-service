
# AI-Powered Income Document Extraction Service

## Overview
This repository introduces an **AI Extraction Service** designed to assist loan officers by extracting and validating income data from uploaded documents (e.g., pay stubs). The service integrates cleanly into an existing microservices architecture without modifying the Underwriting Service API, while enforcing a **human-in-the-loop** workflow for compliance and auditability.

---

## Architecture & Design Rationale

### Service Placement
The **AI Extraction Service** is implemented as a **fourth microservice**, positioned between:
- **Document Service**
- **Underwriting Service**

It is intentionally **not embedded** into either service because:
- Extraction is a distinct concern with its own latency, failure modes, and compliance risks
- It must be independently deployable and feature-flagged
- The Underwriting Service API is immutable by constraint

This preserves service boundaries and prevents AI-related instability from impacting core underwriting flows.

---

### End-to-End Data Flow
1. A loan officer uploads a document via the Angular dashboard  
2. **Document Service**
   - Stores the file in S3
   - Persists metadata in PostgreSQL  
3. Loan officer triggers extraction  
4. Angular frontend calls  


POST /api/v1/extractions/trigger

with `applicationId` and `documentId`
5. **AI Extraction Service**
- Fetches raw document text
- Redacts PII *inside the trust boundary*
- Sends redacted text to the LLM
- Parses a strict JSON response
- Runs discrepancy analysis against self-reported income
- Persists results with status `PENDING_REVIEW`
6. Results are returned to the dashboard
7. Loan officer:
- Reviews extracted fields
- Edits values if needed
- Adds notes
- Submits an approval decision
8. **Only after status = `APPROVED` or `EDITED_AND_APPROVED`**  
can the Underwriting packet endpoint be called

> **Key guarantee:** Human review is enforced at the **API level**, not just in the UI.

---

## Key Design Decisions

### PII Redaction Strategy
- All PII redaction occurs **before any external API call**
- Implemented using **deterministic regex-based patterns**
- Handles:
- Social Security Numbers
- Bank account & routing numbers
- Addresses
- Employee names in specific document sections

**Trade-off**
- Regex does not catch all PII patterns
- Chosen deliberately for:
- Zero latency
- No external dependency
- Fully auditable behavior

**Planned enhancement**
- Integrate AWS Comprehend PII detection in a follow-up sprint to increase recall

---

### LLM Interaction Design
- Uses a **strict JSON-only system prompt**
- No free-form or natural language output allowed

**Why this matters**
- Ensures schema validation
- Makes hallucinations detectable
- Prevents silent corruption from string parsing

**Failure handling**
- Invalid JSON → extraction fails
- Loan officer is notified explicitly

---

### Alternatives Considered
**AWS Textract**
- Strong on standardized forms
- Weak on semi-structured or irregular pay stubs

**Claude (chosen)**
- Superior instruction-following
- Handles layout variation better

**Recommended future enhancement**
- Hybrid approach:
- Textract → layout & OCR
- Claude → semantic interpretation

---

## Production Readiness

### Error Handling
- Spring Retry with exponential backoff  
- 3 attempts  
- 2× multiplier
- After retries are exhausted:
- Status → `EXTRACTION_FAILED`
- Loan officer is notified
- Application does **not stall silently**

---

### Hallucination Detection & Safeguards
Schema validation automatically flags records when:
- Monetary values are negative
- Confidence score < **0.70**
- Pay period dates are logically invalid

Result:
- Status → `NEEDS_CLARIFICATION`
- Officer must explicitly acknowledge before proceeding

---

### Observability & Monitoring
- Spring Actuator enabled
- Prometheus endpoint:


/actuator/prometheus



**Key alerts**
- LLM API latency (p99)
- Extraction failure rate
- Discrepancy flag rate (spikes indicate new document formats)
- Officer review turnaround time

Metrics feed Grafana alongside existing ECS and RDS dashboards.

---

### Security Hardening
- LLM API key stored in **AWS Secrets Manager**
- Injected at ECS task startup
- Never hardcoded or logged
- Endpoint access restricted via JWT:
- `LOAN_OFFICER`
- `ADMIN`
- No raw document text in logs
- Only document IDs and redaction counts
- All internal service calls use **IAM roles**, not shared secrets

---

### Rollback Strategy
- Feature flag disables:


POST /api/v1/extractions/trigger

`
- Reverts officers to manual entry instantly
- No data migration required
- Underwriting packet format remains unchanged
- `incomeVerificationMethod` field preserves audit trace

---

## Where AI Coding Assistants Go Wrong

### Real Risk Example: Regex-Based PII Redaction
An AI assistant may generate:

\d{9}


to match SSNs.

**Why this is dangerous**

* Matches:

  * Zip+4 codes
  * Phone numbers
  * EINs
  * Account numbers
* Misses properly formatted SSNs:

  
  XXX-XX-XXXX
  

**Why it slips through**

* Code compiles
* Unit tests with `"123-45-6789"` pass
* Failure only appears with real documents containing EINs

**Mitigation**

* Use mixed fixture files containing:

  * SSNs
  * EINs
  * Zip+4 codes
  * Phone numbers
* Assert *both* matches and non-matches

> AI assistants generate patterns — they do not validate them against adversarial data.

---

## AI Usage Log (Transparency)

### Interaction 1

* AI drafted an LLM prompt returning a bulleted list
* Rejected due to non-parseable output
* Replaced with strict JSON schema + explicit null handling

### Interaction 2

* AI generated Spring Security config using `antMatchers()`
* Deprecated in Spring Boot 3.x
* Caught via official migration guide
* Would have failed at runtime under strict config

### Interaction 3

* AI produced discrepancy logic using flat percentage threshold
* Ignored directionality
* Enhanced to annotate whether income was over- or under-reported
* Direction matters for compliance review

---

## Summary

This service is designed to:

* Improve operational efficiency
* Preserve compliance guarantees
* Keep humans in control
* Fail loudly, not silently
* Treat AI as an *assistive* system, not an authority

The architecture favors **auditability, determinism, and rollback safety** over unchecked automation.



