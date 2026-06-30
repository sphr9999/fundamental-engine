---
description: Write professional API specification for Spring Boot APIs including validation, mapping, errors, and SVG sequence diagram.
---

# Write API Spec

## Goal

Generate a professional API specification for developers, QA, BA, and integration teams.

## Steps

1. Read AGENTS.md
2. Read .agent/rules/project-rules.md if available
3. Read .agent/skills/api-spec-writer.md if available
4. Identify controller endpoint, HTTP method, path, and module
5. Identify authentication and authorization requirements
6. Identify request headers and response headers
7. Identify request DTO fields, data types, required fields, and examples
8. Identify validation rules and validation order
9. Identify service flow and business rules
10. Identify external integration request/response mapping
11. Identify Redis/cache and DB side effects
12. Identify response DTO fields and examples
13. Identify error response format and error cases
14. Generate sequence diagram as inline SVG if requested
15. Do not invent business rules
16. Use TODO: verify business rule for unclear logic

## Required Output Sections

1. Tổng quan API
2. Endpoint
3. Authentication & Authorization
4. Request Headers
5. Response Headers
6. Request Specification
7. Validation Rules
8. Response Specification
9. Error Response
10. Sequence Diagram
11. Request/Response Mapping
12. Business Rules
13. Side Effects
14. Notes / Open Questions
