# Workflow: Add New Kafka Notification

**Trigger:** `/add-kafka-notification`
**Purpose:** Step-by-step guide to implement a new notification type triggered by Kafka events in `notification-service`.
**Prerequisite Skill:** Read `.agent/skills/notification-kafka-pattern/SKILL.md` before executing this workflow.

---

## Prerequisites — Gather Information

Before starting, collect these inputs from the user or requirements document:

| # | Input | Example | Required |
|---|---|---|---|
| 1 | **Feature name** (English, PascalCase) | `Dunning`, `TopUp`, `PolicyLoan` | ✅ |
| 2 | **Kafka topic** — new dedicated topic or existing shared topic? | `notification-event-dunning` (new) or use existing PS topic | ✅ |
| 3 | **Kafka message structure** (fields) | `holderId`, `policyNumber`, `milestone`, ... | ✅ |
| 4 | **How to identify user** — by `partyId` or `businessPartner` (BP)? | `partyId` for core events, `BP` for eform events | ✅ |
| 5 | **Notification sub-types** (milestones/statuses) | `DUNNING_T`, `DUNNING_T_55` or `*_COMPLETE`, `*_REJECT`, `*_PENDING` | ✅ |
| 6 | **Template details** per sub-type: title, content, template_code | Title: "Thông báo đóng phí", Code: `NTF.00000760` | ✅ |
| 7 | **Additional payload fields** beyond `policyNumber` | `totalDuePremium`, `nextDueDate` | Optional |
| 8 | **Deep link target** (screen name, URL) | `NOTIFICATION_DUNNING`, `/notification/dunning` | Optional |
| 9 | **SRS document** or business requirements | Link to Confluence/Jira | Recommended |

---

## Step 1: Read the Notification Skill

```
Read: .agent/skills/notification-kafka-pattern/SKILL.md
```

Understand the architecture, conventions, and reference implementation before writing any code.

---

## Step 2: Add Enum Values

**File:** `notification-service/src/main/java/life/mbageas/notification/enums/UserNotificationSubTypeEnum.java`

**Action:** Add new enum values for each notification sub-type.

**Convention:**
- Enum name: `SCREAMING_SNAKE_CASE` matching the template `type` in DB
- Each enum has a `code` field
- Group related enums together with a comment

**Example:**
```java
// Dunning
DUNNING_T("DUNNING_T"),
DUNNING_T_55("DUNNING_T_55"),
```

**Verify:** Enum compiles and values are unique.

---

## Step 3: Create Kafka DTO

**Location:** `notification-service/src/main/java/life/mbageas/notification/pojo/dto/`
**File name:** `Notification{Feature}DTO.java`

**Action:** Create DTO matching the Kafka message structure.

**Rules:**
- Use `@Data` / `@NoArgsConstructor` / `@AllArgsConstructor` (Lombok)
- Use `@JsonIgnoreProperties(ignoreUnknown = true)` for forward compatibility
- Field names must match Kafka message JSON keys exactly (or use `@JsonProperty`)
- Do NOT log PII fields (phone, email, identity) — only log IDs

**Example reference:** See `NotificationDunningDTO.java`

---

## Step 4: Create Strategy Class

**Location:** `notification-service/src/main/java/life/mbageas/notification/strategy/`
**File name:** `{Feature}Notification.java`

**Action:** Implement `NotificationStrategyPatternService`.

**Checklist:**
- [ ] `@Slf4j @Component @RequiredArgsConstructor` annotations
- [ ] `getEventType()` returns unique event type string
- [ ] `getTemplateByType()` delegates to `helper.getTemplate(subType)`
- [ ] `sendMessagePlatform()` delegates to `notificationService.sendNotification()`
- [ ] `saveNotificationLog()` orchestration:
  - [ ] Convert incoming data to specific DTO via `helper.convertObject()`
  - [ ] Map DTO fields → `UserNotificationSubTypeEnum`
  - [ ] Get template from DB
  - [ ] Look up user accounts via `helper.getListUserAccount()`
  - [ ] Collect & **deduplicate** device tokens
  - [ ] Build `GatewayNotificationRequest2` request
  - [ ] Send push only if device tokens are **not empty**
  - [ ] Save `UserNotification` per account via helper
  - [ ] Wrap entire method in try-catch with error logging

**Critical rules from skill:**
- Deduplicate device tokens (`distinct()`)
- Skip Messaging Platform call if no tokens
- Use flat `additionalData` map (not nested)
- Use `${policyNumber}` placeholder format
- Serialize PayloadData with ObjectMapper

---

## Step 5: Create or Update Kafka Consumer

### Option A: New Dedicated Consumer (new Kafka topic)

**Location:** `notification-service/src/main/java/life/mbageas/notification/consumer/`
**File name:** `{Feature}NotificationConsumer.java`

**Template:**
```java
@Slf4j
@Component
@RequiredArgsConstructor
public class {Feature}NotificationConsumer {
    private static final String EVENT_{FEATURE} = "{EVENT_TYPE}";
    private final NotificationStrategyService notificationStrategyService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "${kafka.producer.topics.notification-event-{feature}}",
        groupId = "${kafka.consumer.group}"
    )
    public void create{Feature}Notification(Map<String, Object> message) {
        log.info("Received {Feature} Notification: {}", message);
        Notification{Feature}DTO dto = objectMapper.convertValue(message, Notification{Feature}DTO.class);
        notificationStrategyService.send(dto, EVENT_{FEATURE});
    }
}
```

**Also add** the Kafka topic property in `application.yml` / `application-{env}.yml`:
```yaml
kafka:
  producer:
    topics:
      notification-event-{feature}: {actual-topic-name}
```

### Option B: Use Existing Shared Consumer (existing topic)

No new consumer needed. Ensure:
- The DTO structure matches the shared consumer's expected format (e.g., `NotificationPSDTO` with `after.eventType`)
- The `getEventType()` in strategy matches the value in the Kafka message's event type field

---

## Step 6: Create Database Migration (Template Insert)

**Location:** `database-migration/src/main/resources/db-notification-service/`
**File name:** `{next_number}_insert_into_template_{feature}.sql`

**Action:**
1. Check the latest migration number in the directory
2. Create SQL file with template INSERT statements

**Template:**
```sql
-- Insert {Feature} notification templates
INSERT INTO template (type, title, content, template_code, target_screen, target_url)
VALUES
    ('{SUB_TYPE_1}', '{Title}', '{Content with ${policyNumber}}', '{NTF.XXXXXXXX}', '{TARGET_SCREEN}', '{TARGET_URL}'),
    ('{SUB_TYPE_2}', '{Title}', '{Content with ${policyNumber}}', '{NTF.XXXXXXXX}', '{TARGET_SCREEN}', '{TARGET_URL}');
```

**Also add** the changeset reference in the Liquibase changelog file if required.

**Rules:**
- `type` value MUST exactly match `UserNotificationSubTypeEnum` name
- Placeholder format: `${policyNumber}` (NOT Thymeleaf)
- Template codes (`NTF.XXXXXXXX`) must be confirmed with Messaging Platform team
- Use `TODO: confirm template_code with Messaging Platform team` if code is unknown

---

## Step 7: Add Kafka Topic Configuration

If using a new dedicated topic (Step 5 Option A):

**Files to update:**
- `notification-service/src/main/resources/application.yml` (or per-environment files)

**Add:**
```yaml
kafka:
  producer:
    topics:
      notification-event-{feature}: {topic-name-from-core-team}
```

**Use `TODO: confirm topic name with Core team` if the topic name is unknown.**

---

## Step 8: Write SRS Documentation

**Location:** `notification-service/docs/srs-{feature}-notification.md`

**Use the SRS Writer skill** (`.agent/skills/srs-writer/SKILL.md`) or follow the existing SRS format from `srs-dunning-notification.md`.

**Required sections:**
1. Meta Information (feature name, status, tech stack, actors)
2. Business Intent (problem, flow, glossary)
3. Architecture & Actors (sequence diagram in Mermaid)
4. Core Constraints & Business Rules (ownership, data integrity, idempotency)
5. State Machine & Lifecycle
6. Implementation Contract (file links to all created files)

---

## Step 9: Verify

### 9.1 Build Check

```bash
rtk mvn clean install -DskipTests -pl notification-service
```

Ensure no compilation errors.

### 9.2 Code Review Checklist

- [ ] **Enum** values added and unique
- [ ] **DTO** has `@JsonIgnoreProperties(ignoreUnknown = true)`
- [ ] **Strategy** is `@Component` with unique `getEventType()`
- [ ] **Strategy** deduplicates device tokens before sending
- [ ] **Strategy** skips Messaging Platform call when no tokens
- [ ] **Strategy** wraps logic in try-catch
- [ ] **Strategy** does NOT log PII (only IDs and references)
- [ ] **Consumer** logs incoming message at INFO
- [ ] **Migration** SQL has correct `type` matching enum name
- [ ] **PayloadData** uses flat additionalProperties (not nested)
- [ ] **application.yml** has new Kafka topic property (if dedicated consumer)
- [ ] **SRS** document created with sequence diagram

### 9.3 Unit Tests (if applicable)

```bash
rtk mvn test -pl notification-service -Dtest={Feature}NotificationTest
```

### 9.4 Files Changed Summary

Generate a summary of all files created/modified:

```md
## Summary: Add {Feature} Notification
## Files Changed:
- `enums/UserNotificationSubTypeEnum.java` — Added {N} new enum values
- `pojo/dto/Notification{Feature}DTO.java` — [NEW] Kafka message DTO
- `strategy/{Feature}Notification.java` — [NEW] Strategy implementation
- `consumer/{Feature}NotificationConsumer.java` — [NEW] Kafka consumer (if dedicated)
- `db-notification-service/{N}_insert_into_template_{feature}.sql` — [NEW] Template migration
- `application.yml` — Added Kafka topic property (if dedicated)
- `docs/srs-{feature}-notification.md` — [NEW] SRS documentation
## Risk Notes:
- New Kafka topic requires Core team coordination
- Template codes require Messaging Platform team confirmation
- No destructive DB changes (INSERT only)
```
