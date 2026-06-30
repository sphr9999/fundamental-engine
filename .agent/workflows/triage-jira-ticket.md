---
description: Triage Jira bug ticket using mbal-jira-triage MCP to read ticket, query Kibana logs, trace source code, and produce root cause analysis.
---

# Triage Jira Ticket

## Goal

Read a Jira ticket, gather evidence from logs and source code, and produce a structured root cause analysis — without modifying any code.

## Steps

### Phase 1: Gather Ticket Context

1. Read AGENTS.md
2. Call MCP tool `get_ticket_context` with the Jira ticket key or URL
3. From the response, extract:
   - `policyNumbers` and `bpNumbers`
   - `initialTicketSummary` — understand what the user/reporter is describing
   - `suggestedKibanaQueries` — use these as starting queries
   - `created` and `updated` — ticket creation and last update timestamps
   - `eventTimestamps` — extracted event datetimes from ticket text (when bug actually occurred)
   - `suggestedTimeRange` — computed time window for Kibana queries (based on event timestamps or ticket created date fallback)
   - `nextStep` — follow the guidance (if bpNumber found → query Kibana; if not → manual inspection)
4. If `bpNumbers` is empty and `bpEnrichedFrom` exists, note that bpNumber was resolved from Digital Platform API (not from ticket text)

### Phase 2: Query Kibana Logs

1. Call MCP tool `query_kibana` with the ERROR-level suggested query:
   - Use `days` from `suggestedTimeRange.days` (computed from event timestamps or ticket created date). Only widen to 14+ days if initial results are insufficient.
   - Use `size: 50` initially (increase to 100–200 if needed)
2. From the response, analyze:
   - `errorSummary` — identify the dominant error pattern, frequency, and affected services
   - `hits` — read individual log entries for `stackTrace`, `serviceUrl`, `httpMethod`, `traceId`
3. If errors span multiple services, run additional queries filtering by service:
   - Example: `logMessage.bpNumber : "XXXXXXXXXX" AND logMessage.level : "ERROR"`
4. If needed, query INFO-level logs to understand the full request flow:
   - `logMessage.bpNumber : "XXXXXXXXXX"` (without ERROR filter)
   - Or trace a specific request: `logMessage.trace.id : "abc123..."`
5. Mask PII (customer name, phone, email, identity documents) when summarizing evidence

### Phase 3: Trace Source Code

1. From the stackTrace, identify the exact Java class, method, and line number where the exception is thrown
2. Use grep/view to read the source code at the failure point
3. Trace the code path: Controller → Service → Repository → Client/Integration
4. Identify the business rule or validation that caused the exception
5. Check if the error message returned to the user accurately describes the actual problem
6. Check related code for:
    - Status/state checks that might reject the request
    - Data format mismatches (policy number padding, date formats)
    - External API call failures (Digital Platform, eBao, Payment Gateway)
    - Redis/cache stale data
    - Race conditions or duplicate request handling

### Phase 4: Root Cause Analysis

1. Correlate ticket description + Kibana evidence + source code to determine:
    - **What happened**: The exact failure and error message
    - **Why it happened**: The business rule or technical condition that triggered the error
    - **Is it a bug or expected behavior?**: Determine if the code is working as designed but with poor UX, or if there is an actual logic error
2. If the issue is already resolved (ticket status = Closed), note what fixed it and whether it could recur
3. Do not modify code unless explicitly asked

## Output Format

Create an artifact `bug_analysis_{TICKET_KEY}.md` with:

- **Ticket Info**: Key, summary, status, policy, BP number, reporter, timestamps
- **Timeline**: Chronological list of ERROR events from Kibana with timestamps and messages
- **Root Cause Analysis**:
  - Error point (class, method, line, exception message)
  - Source code snippet showing the validation/logic that failed
  - Explanation of why the condition was triggered
  - Whether this is a bug vs. expected behavior with bad UX
- **Impact**: Data impact, user impact, frequency
- **Recommendations** (if applicable):
  - Code fix suggestions (error message improvement, validation logic, etc.)
  - Frontend/UX improvements
  - Logging improvements
- **Kibana Queries for Further Investigation**: Ready-to-use queries for follow-up

## Tips

- When `query_kibana` returns many hits, focus on `errorSummary` first — it groups errors by message and counts occurrences
- Use `sampleTraceIds` from errorSummary to deep-dive into specific requests
- Policy numbers may appear in 12-digit or 17-digit format (padded with leading zeros) — check both
- The index pattern `filebeat-*-lamb*,filebeat-*-eform*` covers lamb and eform services. For payment or notification services, adjust the `index` parameter
- Use `suggestedTimeRange` from get_ticket_context to set the Kibana `days` parameter — this is more precise than defaulting to 14 days
- Compare the timestamp of errors with the ticket creation time and `eventTimestamps` to confirm the reported issue matches the logs
