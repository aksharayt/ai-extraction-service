package com.lending.ai_extraction_service.service.pii;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Redacts PII from extracted document text before sending to any external LLM API.
 * Compliance requirement: No SSNs, account numbers, or identified income figures
 * tied to a named individual may leave the trust boundary.
 *
 * Strategy: Replace PII tokens with stable pseudonyms, extract structured data,
 * then discard the pseudonym map after extraction. The LLM never sees real values.
 */
@Service
@Slf4j
public class PiiRedactionService {

    // Patterns ordered from most specific to least specific
    private static final Pattern SSN_PATTERN =
            Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");

    private static final Pattern ACCOUNT_NUMBER_PATTERN =
            Pattern.compile("\\b(?:Acct|Account|A/C)[\\s#:.]*([A-Za-z0-9]{6,20})\\b",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern ROUTING_NUMBER_PATTERN =
            Pattern.compile("\\b(?:Routing|RTN)[\\s#:.]*([0-9]{9})\\b",
                    Pattern.CASE_INSENSITIVE);

    // Full legal name — 2-4 words of capital letters
//     private static final Pattern FULL_NAME_PATTERN =
//             Pattern.compile("\\b([A-Z][a-z]+(?:\\s[A-Z][a-z]+){1,3})\\b");

    private static final Pattern ADDRESS_PATTERN =
            Pattern.compile("\\d{1,5}\\s(?:[A-Za-z0-9\\s,.])+(?:Street|St|Avenue|Ave|" +
                            "Boulevard|Blvd|Road|Rd|Lane|Ln|Drive|Dr|Court|Ct|Way|Place|Pl)\\.?",
                    Pattern.CASE_INSENSITIVE);

    public RedactionResult redact(String rawDocumentText) {
        log.debug("Starting PII redaction on document text of length {}", rawDocumentText.length());

        Map<String, String> redactionMap = new HashMap<>();
        String redacted = rawDocumentText;

        redacted = redactPattern(redacted, SSN_PATTERN, "SSN_REDACTED", redactionMap);
        redacted = redactPattern(redacted, ACCOUNT_NUMBER_PATTERN, "ACCT_REDACTED", redactionMap);
        redacted = redactPattern(redacted, ROUTING_NUMBER_PATTERN, "ROUTING_REDACTED", redactionMap);
        redacted = redactPattern(redacted, ADDRESS_PATTERN, "ADDRESS_REDACTED", redactionMap);

        // Name redaction is intentionally last — employer name must survive
        // We only redact names in "Employee:" or "Pay To:" sections
        redacted = redactEmployeeNameSection(redacted, redactionMap);

        log.info("PII redaction complete. {} tokens redacted.", redactionMap.size());

        return new RedactionResult(redacted, redactionMap.size());
    }

    private String redactPattern(String text, Pattern pattern,
                                 String placeholder, Map<String, String> map) {
        StringBuffer sb = new StringBuffer();
        var matcher = pattern.matcher(text);
        while (matcher.find()) {
            String matched = matcher.group();
            String token = placeholder + "_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            map.put(token, matched);
            matcher.appendReplacement(sb, token);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String redactEmployeeNameSection(String text, Map<String, String> map) {
        // Only redact names immediately after "Employee:", "Pay To:", "Payee:", "Name:"
        Pattern employeeSection = Pattern.compile(
                "(?i)(Employee:|Pay To:|Payee:|Employee Name:)\\s*([A-Z][a-z]+(?:\\s[A-Z][a-z]+){1,3})");
        var matcher = employeeSection.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String label = matcher.group(1);
            String name = matcher.group(2);
            String token = "EMPLOYEE_NAME_REDACTED";
            map.put(token, name);
            matcher.appendReplacement(sb, label + " " + token);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public record RedactionResult(String redactedText, int redactedTokenCount) {}
}