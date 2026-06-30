---
name: notification-kafka-pattern
description: >
  Architecture patterns, conventions, and reference implementation for building
  Kafka-driven notification features in notification-service. Covers Strategy pattern,
  Consumer, DTO, Enum, Template migration, Messaging Platform integration, and
  UserNotification persistence. Use when adding any new notification type from Kafka.
---

# Notification Kafka Pattern — Skill Guide

## 1. When to Use This Skill

Use this skill when:
- Adding a **new notification type** triggered by a Kafka event
- Modifying an existing notification strategy
- Debugging notification delivery issues
- Understanding the notification architecture

**Always read this skill before implementing any notification feature.**

---

## 2. Architecture Overview

### 2.1 High-Level Flow

```
Kafka Topic
  → Consumer (deserialize message → DTO)
    → NotificationStrategyService.send(dto, eventType)
      → NotificationStrategyFactory.getStrategy(eventType)
        → Strategy.saveNotificationLog(dto)
          ├── Convert DTO
          ├── Map event fields → UserNotificationSubTypeEnum
          ├── helper.getTemplate(subType) → Template from DB
          ├── helper.getListUserAccount() → user-service REST → device tokens
          ├── Build GatewayNotificationRequest2
          ├── sendMessagePlatform() → Messaging Platform REST → transactionId
          └── helper.createUserNotification() + save → DB
```

### 2.2 Strategy Pattern Components

| Component | Class | Location | Role |
|---|---|---|---|
| **Interface** | `NotificationStrategyPatternService` | `strategy/` | Contract for all notification strategies |
| **Factory** | `NotificationStrategyFactory` | `factory/` | Auto-registers all `@Component` strategies via `getEventType()` key |
| **Dispatcher** | `NotificationStrategyService` | `service/` | Routes `send(dto, eventType)` to correct strategy |
| **Strategy** | e.g. `DunningNotification` | `strategy/` | Implements processing logic for a specific event type |
| **Helper** | `NotificationsHelper` | `helper/` | Shared utilities (template lookup, user query, save notification) |

### 2.3 Interface Contract

Every strategy must implement:

```java
public interface NotificationStrategyPatternService {
    <T> String sendMessagePlatform(T template);     // Call Messaging Platform → returns transactionId
    <T> void saveNotificationLog(T data);            // Main orchestration entry point
    Template getTemplateByType(UserNotificationSubTypeEnum type); // Lookup template from DB
    String getEventType();                           // Unique key for factory registry
}
```

**Critical:** `getEventType()` must return a unique string matching the event type constant used by the Consumer.

---

## 3. Project Structure & File Locations

All paths relative to `notification-service/src/main/java/life/mbageas/notification/`:

```
├── consumer/
│   ├── DunningNotificationConsumer.java          # Dedicated consumer (1 topic → 1 strategy)
│   ├── KafkaMessageNotificationPSConsumer.java   # Shared consumer (1 topic → N strategies via eventType)
│   └── KafkaMessageNotificationAfterSalesConsumer.java
├── strategy/
│   ├── NotificationStrategyPatternService.java   # Interface
│   ├── DunningNotification.java                  # Strategy impl
│   ├── TopUpNotification.java
│   ├── SurrenderNotification.java
│   └── generalform/                              # Sub-group for general form strategies
│       ├── PolicyLoanNotification.java
│       └── ...
├── factory/
│   └── NotificationStrategyFactory.java          # Auto-registry
├── service/
│   ├── NotificationStrategyService.java          # Dispatcher
│   ├── NotificationService.java                  # Interface (sendNotification, etc.)
│   └── impl/
│       └── NotificationServiceImpl.java          # Messaging Platform REST call
├── helper/
│   └── NotificationsHelper.java                  # Shared logic
├── pojo/
│   ├── dto/
│   │   ├── NotificationDunningDTO.java           # Kafka message DTO
│   │   ├── PayloadData.java                      # DB storage payload
│   │   ├── GatewayNotificationRequest2.java      # Messaging Platform request
│   │   └── AccountInfoDTO.java                   # From user-service
│   ├── entity/
│   │   ├── Template.java                         # JPA entity
│   │   └── UserNotification.java                 # JPA entity
│   └── request/
│       └── GetListUserInfoRequest.java           # user-service request
├── enums/
│   ├── UserNotificationSubTypeEnum.java          # 80+ sub-types
│   ├── UserNotificationTypeEnum.java             # COMMON, USER
│   ├── TemplateEnum.java                         # Lamb_FreeText, Lamb-NewLetter
│   └── TypeEnum.java                             # TOPIC, TOKEN
└── repository/ (or response/)
    ├── TemplateRepository.java
    └── UserNotificationRepository.java
```

### Database Migrations

Location: `database-migration/src/main/resources/db-notification-service/`

| File | Purpose |
|---|---|
| `01_add_table_user_notification.sql` | Create `user_notification` table |
| `02_add_table_template.sql` | Create `template` table |
| `03_alter_table_user_notification.sql` | Add `seen_on_list` column |
| `14_insert_into_template_dunning.sql` | Insert Dunning templates (reference for new migrations) |

---

## 4. Consumer Patterns

### 4.1 Dedicated Consumer (New Kafka Topic)

Use when the notification has its **own dedicated Kafka topic**.

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class DunningNotificationConsumer {
    private static final String EVENT_DUNNING = "DUNNING";
    private final NotificationStrategyService notificationStrategyService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "${kafka.producer.topics.notification-event-dunning}",
        groupId = "${kafka.consumer.group}"
    )
    public void createDunningNotification(Map<String, Object> message) {
        log.info("Received Dunning Notification: {}", message);
        NotificationDunningDTO dto = objectMapper.convertValue(message, NotificationDunningDTO.class);
        notificationStrategyService.send(dto, EVENT_DUNNING);
    }
}
```

**When to use:** The Kafka message has a unique structure not shared with other events.

### 4.2 Shared Consumer (Existing Kafka Topic)

Use when the notification shares a topic with other event types (e.g., PS topic, AfterSales topic).

```java
@KafkaListener(
    topics = "${kafka.producer.topics.notification-event-ps}",
    groupId = "${kafka.consumer.group}"
)
public void createPSNotification(Map<String, Object> message) {
    NotificationPSDTO dto = objectMapper.convertValue(message, NotificationPSDTO.class);
    String eventType = dto.getAfter().getEventType(); // Strategy resolved from message content
    notificationStrategyService.send(dto, eventType);
}
```

**When to use:** Multiple notification types arrive on the same Kafka topic, differentiated by a field in the message.

---

## 5. Strategy Implementation Guide

### 5.1 Standard Strategy Template

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class NewFeatureNotification implements NotificationStrategyPatternService {

    private final NotificationsHelper helper;
    private final NotificationService notificationService;

    // 1. Unique event type key — must match Consumer's constant
    @Override
    public String getEventType() {
        return "NEW_FEATURE";
    }

    // 2. Template lookup
    @Override
    public Template getTemplateByType(UserNotificationSubTypeEnum type) {
        return helper.getTemplate(type);
    }

    // 3. Send to Messaging Platform
    @Override
    public <T> String sendMessagePlatform(T template) {
        return notificationService.sendNotification((GatewayNotificationRequest2) template);
    }

    // 4. Main orchestration — this is where all the logic goes
    @Override
    public <T> void saveNotificationLog(T data) {
        try {
            // Step A: Convert incoming data to specific DTO
            NewFeatureDTO dto = helper.convertObject(data, NewFeatureDTO.class);

            // Step B: Determine sub-type for template lookup
            UserNotificationSubTypeEnum subType = mapToSubType(dto);
            Template template = getTemplateByType(subType);
            if (template == null) {
                log.warn("Template not found for subType: {}", subType);
                return;
            }

            // Step C: Look up user accounts & device tokens
            GetListUserInfoRequest req = new GetListUserInfoRequest();
            req.setBps(List.of(dto.getBusinessPartner()));
            // OR for partyId-based: req.setPartyIds(List.of(dto.getHolderId()));
            List<AccountInfoDTO> accounts = helper.getListUserAccount(req);

            // Step D: Collect & deduplicate device tokens
            List<String> deviceTokens = accounts.stream()
                .filter(a -> a.getDeviceTokenList() != null)
                .flatMap(a -> a.getDeviceTokenList().stream())
                .distinct()
                .collect(Collectors.toList());

            // Step E: Send push notification (only if tokens exist)
            String transactionId = null;
            if (!deviceTokens.isEmpty()) {
                GatewayNotificationRequest2 request = buildGatewayRequest(
                    template, deviceTokens, dto
                );
                transactionId = sendMessagePlatform(request);
            } else {
                log.warn("No device tokens found, skipping push notification");
            }

            // Step F: Save notification records per account
            for (AccountInfoDTO account : accounts) {
                UserNotification entity = helper.createUserNotification(
                    template,
                    dto.getPolicyNumber(),
                    account,
                    transactionId,
                    subType
                    // Optional: additionalData map for extra fields
                );
                helper.saveUserNotification(entity);
            }

        } catch (Exception e) {
            log.error("Error processing NewFeature notification: {}", e.getMessage(), e);
        }
    }
}
```

### 5.2 User Lookup Patterns

Two ways to look up users depending on what identifier the Kafka message provides:

| Identifier | Request Field | Use Case |
|---|---|---|
| `partyId` / `holderId` | `req.setPartyIds(...)` | Dunning (core system identifier) |
| `businessPartner` (BP) | `req.setBps(...)` | EForm, PS events (linked account identifier) |

### 5.3 Additional Data in PayloadData

When your notification needs extra fields beyond `policyNumber` in the stored JSON:

```java
// Use additionalData map for extra fields
Map<String, String> additionalData = new HashMap<>();
additionalData.put("totalDuePremium", dto.getTotalDuePremium());
additionalData.put("nextDueDate", dto.getNextDueDate());

UserNotification entity = helper.createUserNotification(
    template, policyNumber, account, transactionId, subType, additionalData
);
```

**CRITICAL RULE:** Additional data uses **flat structure** via Jackson `@JsonAnyGetter`/`@JsonAnySetter` on `PayloadData`. Do NOT nest under `additionalProperties` key. The serialized JSON must look like:

```json
{
  "templateCd": "NTF.00000760",
  "type": "token",
  "title": "Thông báo đóng phí",
  "content": "Hợp đồng HN00123456...",
  "policyNumber": "HN00123456",
  "totalDuePremium": "5000000",
  "nextDueDate": "2026-07-01"
}
```

**NOT** like this (wrong):
```json
{
  "templateCd": "...",
  "additionalProperties": { "totalDuePremium": "...", "nextDueDate": "..." }
}
```

---

## 6. Naming Conventions

### 6.1 Classes

| Component | Pattern | Example |
|---|---|---|
| Strategy | `{Feature}Notification` | `DunningNotification`, `TopUpNotification` |
| Consumer | `{Feature}NotificationConsumer` | `DunningNotificationConsumer` |
| Kafka DTO | `Notification{Feature}DTO` | `NotificationDunningDTO` |
| Event Type | `SCREAMING_SNAKE_CASE` | `"DUNNING"`, `"POLICY_TOPUP"` |

### 6.2 Enum Values (`UserNotificationSubTypeEnum`)

| Pattern | Example |
|---|---|
| Simple milestone | `DUNNING_T`, `DUNNING_T_55` |
| Status-based | `TOP_UP_STATUS_COMPLETE`, `TOP_UP_STATUS_REJECT`, `TOP_UP_STATUS_PENDING` |
| Form-based | `REFUND_PREMIUM_OVERPAYMENT_COMPLETE`, `REFUND_PREMIUM_OVERPAYMENT_REJECT` |

Each enum value has a `code` string field — typically the same as the enum name in lowercase or matching a legacy code.

### 6.3 Template Codes

Format: `NTF.XXXXXXXX` (e.g., `NTF.00000760`). Assigned by Messaging Platform team.

### 6.4 Kafka Topic Properties

Format: `kafka.producer.topics.notification-event-{feature}` in `application.yml`.

---

## 7. Database Conventions

### 7.1 Template Table

```sql
INSERT INTO template (type, title, content, template_code, target_screen, target_url)
VALUES (
    'DUNNING_T',                        -- Must match UserNotificationSubTypeEnum exactly
    'Thông báo đóng phí',               -- Title shown in app
    'Hợp đồng ${policyNumber} ...',     -- Body with ${policyNumber} placeholder
    'NTF.00000760',                     -- Messaging Platform template code
    'NOTIFICATION_DUNNING',             -- Deep link target screen
    '/notification/dunning'             -- Deep link URL
);
```

**Placeholder format:** `${policyNumber}` (NOT Thymeleaf `[(${policyNumber})]`).
**Additional placeholder:** `${changeRequestName}` (used by generalform strategies).

### 7.2 Migration File Naming

Format: `{next_sequence_number}_insert_into_template_{feature}.sql`

Example: `14_insert_into_template_dunning.sql`

Check existing migrations in `database-migration/src/main/resources/db-notification-service/` to determine the next sequence number.

---

## 8. Helper Methods Reference (`NotificationsHelper`)

| Method | Purpose | Returns |
|---|---|---|
| `convertObject(source, targetClass)` | Jackson type conversion between DTOs | Target DTO |
| `readJson(data, clazz)` | Parse JSON string to object | Parsed object |
| `getTemplate(subType)` | DB lookup by `UserNotificationSubTypeEnum` | `Template` entity |
| `getListUserAccount(request)` | REST call to user-service `/internal/get-user-info` | `List<AccountInfoDTO>` |
| `extractPolicyHolder(policyNumber)` | REST call to Digital Platform | `holderId` |
| `getListAccountByPartyId(partyId)` | REST GET call to user-service | `PartyBpMappingDTO` |
| `createUserNotification(template, policyNumber, account, transactionId, subType)` | Build `UserNotification` entity | `UserNotification` |
| `createUserNotification(..., additionalData)` | Same + extra fields in PayloadData | `UserNotification` |
| `createUserNotificationForGeneralForm(...)` | Same + replaces `${changeRequestName}` | `UserNotification` |
| `saveUserNotification(entity)` | Persist to DB | void |

---

## 9. Messaging Platform Integration

### Request Structure (`GatewayNotificationRequest2`)

```java
GatewayNotificationRequest2 request = new GatewayNotificationRequest2();
GatewayNotificationContent content = new GatewayNotificationContent();

content.setTemplateCd(template.getTemplateCode());  // e.g. "NTF.00000760"
content.setTo(deviceTokens);                         // List of device token strings
content.setType("token");                            // "token" for targeted, "topic" for broadcast

NotificationBody body = new NotificationBody();
body.setPolicyNumber(dto.getPolicyNumber());
// Add extra fields via additionalProperties map if needed
content.setBody(body);

content.setExecuteType("NOW");                       // "NOW" or "DELAY"
// content.setSendTime("...");                        // Only for DELAY

request.setContent(content);
```

### Authentication

- Basic Auth with `x-api-key` header
- Configured via: `mbal-messaging-platform.domain`, `mbal-messaging-platform.x-api-key`

---

## 10. Common Pitfalls & Rules

| # | Rule | Why |
|---|---|---|
| 1 | **Always deduplicate device tokens** before sending batch | One user may have multiple accounts under same partyId → duplicate pushes |
| 2 | **Skip Messaging Platform call** if device tokens list is empty | Avoid wasted API calls and errors |
| 3 | **Use flat additionalProperties** in PayloadData | Mobile app expects flat JSON structure |
| 4 | **Use `${policyNumber}` placeholder** format, NOT Thymeleaf | Template engine is simple string replacement |
| 5 | **Wrap entire `saveNotificationLog` in try-catch** | Kafka consumer must not crash on processing errors |
| 6 | **Log the incoming Kafka message** at INFO level | Required for traceability and debugging |
| 7 | **Never log raw PII** (phone, email, identity docs) | Security policy — log only IDs and references |
| 8 | **Template `type` must exactly match enum name** | DB lookup uses enum string comparison |
| 9 | **Add `@Component`** annotation on strategy class | Required for Spring auto-detection by `NotificationStrategyFactory` |
| 10 | **Use ObjectMapper** for PayloadData serialization | Ensures `@JsonAnyGetter/@JsonAnySetter` annotations are applied correctly |

---

## 11. Existing Strategies Reference (21 total)

| # | Strategy | Event Type | Consumer |
|---|---|---|---|
| 1 | `DunningNotification` | `DUNNING` | `DunningNotificationConsumer` (dedicated) |
| 2 | `TopUpNotification` | `POLICY_TOPUP` | `KafkaMessageNotificationPSConsumer` (shared) |
| 3 | `SurrenderNotification` | `POLICY_SURRENDER` | Shared PS |
| 4 | `PartialWithdrawNotification` | `POLICY_PARTIAL_WITHDRAWAL` | Shared PS |
| 5 | `ChangePaymentFrequencyNotification` | `POLICY_CHANGE_PAYMENT_FREQUENCY` | Shared PS |
| 6 | `DeleteRiderNotification` | `POLICY_DELETE_RIDER` | Shared PS |
| 7 | `PolicyCancelNotification` | `POLICY_CANCEL` | Shared PS |
| 8 | `ChangePolicyHolderNotification` | `POLICY_CHANGE_POLICYHOLDER` | Shared PS |
| 9 | `CollectionAdjustmentNotification` | `POLICY_COLLECTION_ADJUSTMENT` | Shared PS |
| 10 | `ReinstatementNotification` | `POLICY_REINSTATEMENT` | Shared PS |
| 11 | `ChangeMaintainCustomerNotification` | — | Shared |
| 12 | `ChangeDobGenderNotification` | `POLICY_CHANGE_DOB_GENDER` | Shared PS |
| 13 | `ChangeHealthConditionNotification` | `POLICY_CHANGE_HEALTH_CONDITION` | Shared PS |
| 14 | `ReUwNotification` | `UNDERWRITING` | Shared |
| 15-21 | `generalform/*` strategies | Various | `KafkaMessageNotificationAfterSalesConsumer` |

Use `DunningNotification` as the **primary reference implementation** for dedicated-consumer patterns.
Use `TopUpNotification` as the reference for shared-consumer (PS/AfterSales) patterns.
