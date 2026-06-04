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
