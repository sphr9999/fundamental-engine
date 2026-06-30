---
name: reverse-engineering
description: >
  Reverse engineer source code thành structured documentation.
  5 phases: Discovery → Core Docs → Error & Flows → Diagrams → Shared Docs.
  PHASE 3-5 LÀ BẮT BUỘC — không được skip sau khi xong Phase 2.
when_to_use: khi cần tạo documentation từ source code chưa có docs
version: 8.0.0
changelog: |
  v8: Bigbang RCA — Debug EP ENFORCED in template (was only in output, not template),
      file size ≤15KB hard gate (verify-docs-v8.sh catches ALL oversized),
      api-reference side effects per-endpoint mandatory, QUALITY-CHECKLIST.md per-module,
      evaluation-criteria 26 dims (was 24), verify-docs-v8.sh replaces verify-docs.sh
  v7: GL-Transform RCA — walkthrough per-module coverage, cURL per-module verify,
      runbook 7 required sections + ≥15 grep commands, data-formats.md for file-processing,
      Final Gate v7 checks
  v6: Phase 4 file size check for diagrams + walkthrough anti-skip instruction
  v5: Phase 2 gate threshold ≥80% + hard block + rationalization prevention
  v4: Scale-awareness: batch processing, Phase 4 command riêng, gate=bash script
  v3: Debug EP, config impact, example tests, per-module requirements
  v2: Completion gates, walkthroughs, FE/mobile diagrams, per-module lifecycle
  v1: Initial 5-phase skill
---

## Overview

Quy trình 5 phases. **PHASE 3-5 LÀ BẮT BUỘC** — AI Agent KHÔNG được dừng sau Phase 2.
Đọc file này TRƯỚC khi bắt đầu. Sau mỗi phase, chạy completion gate check.

### Scale-Awareness (v4+)

| Modules | Batch size | /compact | Estimated time |
|---------|-----------|---------|---------------|
| ≤10 | Không cần batch | Không cần | 4-8h total |
| 11-30 | 10-15 per batch | Sau mỗi batch | 15-25h |
| 31-60 | 15-20 per batch | Sau mỗi batch | 25-40h |
| >60 | 20 per batch | Sau mỗi batch | Scale linearly |

⚠️ NẾU ≥30 modules: PHẢI dùng batch processing + `/compact` giữa batches.
Context window HẾT = AI quên gate requirements = skip items.

---

## Phase 1: Discovery (30-60 phút)

**Goal:** Big picture — modules, tech stack, architecture.
**Output BẮT BUỘC:**
- [ ] CLAUDE.md Module Registry filled
- [ ] `docs/architecture/SUMMARY.md` (dùng template `_templates/SUMMARY-template.md`)
- [ ] `docs/architecture/dependency-map/_index.json`
- [ ] `docs/architecture/READING-GUIDE.md` (dùng template `_templates/READING-GUIDE-template.md`)

**Steps:**
1. Scan source root → list modules (pom.xml/package.json/pubspec.yaml/pyproject.toml)
2. Xác định tech stack, framework, ports, DB per module
3. Detect inter-module dependencies (HTTP, MQ, shared DB)
4. Fill Module Registry + Source Directory Mapping
5. Tạo SUMMARY.md, dependency-map, READING-GUIDE.md

---

## Phase 2: Core Docs per Module (60-90 phút/module)

**Goal:** 6 core files per backend module, 5 per frontend/mobile.
**Output BẮT BUỘC per module:**
- [ ] README.md
- [ ] code-structure.md — **PHẢI có section "## Debug Entry Points"** (≥3 rows, CỤ THỂ cho module, dùng template v8)
- [ ] api-reference.md — **≤15KB (v8 BẮT BUỘC — split part1/part2 nếu vượt). Side Effects per endpoint BẮT BUỘC.**
- [ ] api-reference.md — **v9: MỌI POST/PUT/PATCH endpoint PHẢI có field table `| Field | Type | Required | Description | Constraints |`**
  - Nested objects: dot notation (customer.fullName)
  - Enums: list inline (`MALE`, `FEMALE`)  
  - Dates: format explicit (`yyyy-MM-dd`)
  - Required: ✅ (@NotNull/@NotBlank), Optional: ❌
  - ≥3 field rows per write endpoint
  - GET endpoints với query params → liệt kê params
- [ ] db-schema.md (backend only)
- [ ] error-handling.md — **PHẢI có canonical refs `[MODULE:METHOD:/path]`** (≥3 refs)
- [ ] configuration.md — **PHẢI có column "Impact if wrong"** cho critical properties

### v8: Debug Entry Points — Template Enforcement

⚠️ code-structure-template.md v8 ĐÃ CÓ section Debug Entry Points.
AI Agent PHẢI dùng template, KHÔNG tự viết từ scratch.

Pattern: Đọc Package Layout → xác định components → sinh table:
```
| Bug symptom | Start from | Class/Method | Why |
| API error {endpoint} | {Controller} | {method()} | Request entry, validation |
| Business logic sai | {ServiceImpl} | {method()} | Core logic |
| DB query sai/chậm | {Repository} | {query()} | JPA query, index |
| Config không apply | {ConfigClass} | @Value {prop} | yml binding |
| External call fail | {FeignClient} | {feign()} | timeout, circuit breaker |
```

### v8: File Size Gate per Module

SAU KHI tạo xong mỗi module, KIỂM TRA NGAY:
```bash
for f in $(find "docs/architecture/{module}" -name "*.md"); do
  size=$(wc -c < "$f"); [ "$size" -gt 15360 ] && echo "❌ >15KB: $f → SPLIT NGAY" || true
done
```
Nếu ❌ → split TRƯỚC KHI chuyển sang module tiếp. KHÔNG để dồn cuối sprint.

### Phase 2 bổ sung: Data Format Documentation (v7 — NEW)

⚠️ NẾU hệ thống xử lý files (CSV, Excel, XML), events (Kafka, MQ), hoặc batch data:
- [ ] Tạo `{module}/data-formats.md` cho mỗi module xử lý data
- [ ] Document input format: fields, types, required, examples, sample row
- [ ] Document output format: tương tự
- [ ] Document event/message format: payload structure, JSON schema

**Trigger detection:**
```bash
grep -rn "OpenCSV\|Apache POI\|JXLS\|BufferedReader\|CsvReader" {SOURCE_ROOT} --include="*.java"
grep -rn "@KafkaListener\|@RabbitListener\|@StreamListener" {SOURCE_ROOT} --include="*.java"
grep -rn "SftpAdapter\|FTPClient\|S3Client\|BlobClient" {SOURCE_ROOT} --include="*.java"
```
Nếu ≥1 kết quả → BẮT BUỘC tạo `data-formats.md` cho modules liên quan.

**Grep commands cho Phase 2:**
```bash
# Controllers/endpoints
grep -rn "@RequestMapping\|@GetMapping\|@PostMapping\|@PutMapping\|@DeleteMapping" {module}/src
# Entities
grep -rn "@Entity\|@Table\|@Column" {module}/src --include="*.java"
# Config
find {module} -name "application*.properties" -o -name "application*.yml"
grep -rn "@Value\|@ConfigurationProperties" {module}/src --include="*.java"
# Exceptions
grep -rn "catch\|throw\|Exception\|@ExceptionHandler" {module}/src --include="*.java"
# Debug entry points
find {module}/src -name "*Controller.java" -o -name "*ServiceImpl.java"
grep -rl "@FeignClient\|@KafkaListener\|@RabbitListener" {module}/src --include="*.java"
```

**⚠️ COMPLETION GATE Phase 2 — KHÔNG tiến sang Phase 3 nếu chưa đạt (≥80% threshold):**
```bash
echo "=== PHASE 2 GATE ==="
total=0; pass_ref=0; pass_dep=0; pass_cfg=0; pass_df=0

for dir in $(find docs/architecture -mindepth 1 -maxdepth 1 -type d \
  ! -name '_templates' ! -name 'cross-cutting-flows' ! -name 'dependency-map' \
  ! -name 'known-issues' ! -name 'glossary' ! -name 'field-mapping-ui'); do
  mod=$(basename "$dir")
  total=$((total+1))

  # Canonical refs ≥3
  if [ -f "$dir/error-handling.md" ]; then
    refs=$(grep -c '\[.*:.*/' "$dir/error-handling.md" 2>/dev/null || echo 0)
    [ "$refs" -ge 3 ] && pass_ref=$((pass_ref+1))
  fi

  # Debug Entry Points
  if [ -f "$dir/code-structure.md" ]; then
    grep -qi "debug.*entry\|entry.*point" "$dir/code-structure.md" 2>/dev/null && pass_dep=$((pass_dep+1))
  fi

  # v9: Field Schema Coverage
  if [ -f "$dir/api-reference.md" ]; then
    write_eps=$(grep -c "POST\|PUT\|PATCH" "$dir/api-reference.md" 2>/dev/null || echo 0)
    field_tbls=$(grep -c "| Field.*| Type.*| Required\|| Trường.*| Kiểu" "$dir/api-reference.md" 2>/dev/null || echo 0)
    # Also check split files
    for part in "$dir"/api-reference-part*.md "$dir"/api-reference-schemas.md; do
      [ -f "$part" ] && field_tbls=$((field_tbls + $(grep -c "| Field.*| Type.*| Required\|| Trường.*| Kiểu" "$part" 2>/dev/null || echo 0)))
    done
    if [ "$write_eps" -gt 0 ] && [ "$field_tbls" -lt 1 ]; then
      echo "  ❌ $(basename $dir): $write_eps write endpoints but 0 field tables"
      pass_schema=0
    else
      pass_schema=$((pass_schema+1))
    fi
  fi

  # Config Impact
  if [ -f "$dir/configuration.md" ]; then
    grep -qi "impact.*wrong\|impact.*sai" "$dir/configuration.md" 2>/dev/null && pass_cfg=$((pass_cfg+1))
  fi
done

# Data formats (v7)
file_mods=$(grep -rl "OpenCSV\|Apache POI\|@KafkaListener\|SftpAdapter" {SOURCE_ROOT} --include="*.java" 2>/dev/null \
  | sed 's|.*/\([^/]*\)/src/.*|\1|' | sort -u)
df_total=0; df_done=0
for mod in $file_mods; do
  df_total=$((df_total+1))
  [ -f "docs/architecture/$mod/data-formats.md" ] && df_done=$((df_done+1))
done

pct_ref=$((pass_ref * 100 / total))
pct_dep=$((pass_dep * 100 / total))
pct_cfg=$((pass_cfg * 100 / total))
echo "Canonical refs ≥3: $pass_ref/$total ($pct_ref%) — target ≥80%"
echo "Debug Entry Points: $pass_dep/$total ($pct_dep%) — target ≥80%"
echo "Config Impact: $pass_cfg/$total ($pct_cfg%) — target ≥80%"
echo "Data formats (v7): $df_done/$df_total file-processing modules"

[ "$pct_ref" -lt 80 ] && echo "❌ BLOCK: canonical refs $pct_ref% < 80%"
[ "$pct_dep" -lt 80 ] && echo "❌ BLOCK: debug EP $pct_dep% < 80%"
[ "$pct_cfg" -lt 80 ] && echo "❌ BLOCK: config impact $pct_cfg% < 80%"
[ "$df_total" -gt 0 ] && [ "$df_done" -lt "$df_total" ] && echo "❌ BLOCK: data-formats $df_done/$df_total"
```

**KHÔNG ĐƯỢC rationalize "good enough":**
| AI muốn nói... | Tại sao SAI | Phải làm gì |
|----------------|-------------|-------------|
| "80% là đủ rồi" | 80% là MINIMUM, target 100% | Fix remaining modules |
| "Module này không cần refs" | CÓ api-reference = CÓ THỂ thêm refs | Đọc api-ref → thêm refs |
| "Utility module skip được" | Utility VẪN có error patterns | Thêm ≥3 refs |

---

## Phase 3: Error, Flows, Known Issues (2-4 giờ tổng)

**⚠️ PHASE NÀY BẮT BUỘC — KHÔNG ĐƯỢC SKIP**

### 3.1 error-patterns.md (platform-wide)
**Output:** `docs/architecture/error-patterns.md` (dùng template `_templates/error-patterns-template.md`)

```bash
grep -rn "@ControllerAdvice\|@ExceptionHandler\|GlobalException" {SOURCE_ROOT} --include="*.java"
grep -rn "ErrorResponse\|ApiResponse\|BaseResponse" {SOURCE_ROOT} --include="*.java"
grep -rn "ErrorDecoder\|FeignException\|FallbackFactory" {SOURCE_ROOT} --include="*.java"
```

### 3.2 cross-cutting-flows/ (per domain)
**Output:** `docs/architecture/cross-cutting-flows/_index.md` + per-domain .md files
(dùng template `_templates/cross-cutting-flow-template.md`)

```bash
grep -rn "@FeignClient" {SOURCE_ROOT} --include="*.java"
grep -rn "@KafkaListener\|@RabbitListener" {SOURCE_ROOT} --include="*.java"
grep -rn "RestTemplate\|WebClient\|OpenFeign" {SOURCE_ROOT} --include="*.java"
```

Tạo ÍT NHẤT: 1 flow file per business domain.
Mỗi flow: Mermaid sequence diagram + steps + services involved.

### 3.3 known-issues/ (per domain)
**Output:** `docs/architecture/known-issues/_index.md` + per-domain .md files

```bash
grep -rn "TODO\|FIXME\|HACK\|workaround\|hardcode\|Thread.sleep" {SOURCE_ROOT} --include="*.java"
grep -rn "System.out.println\|e.printStackTrace" {SOURCE_ROOT} --include="*.java"
grep -rn "@Deprecated\|deprecated" {SOURCE_ROOT} --include="*.java"
```

### 3.4 glossary/ (per domain)
**Output:** `docs/architecture/glossary/_index.md` + per-domain .md files

```bash
grep -rn "enum.*Status\|public enum" {SOURCE_ROOT} --include="*.java"
grep -rn "public static final" {SOURCE_ROOT} --include="*.java" | head -30
```

**⚠️ COMPLETION GATE Phase 3:**
```
error-patterns.md exists?
cross-cutting-flows/ có ≥3 flow files?
known-issues/ có ≥1 file per domain?
glossary/ có ≥2 files?
```

---

## Phase 4: Diagrams + Walkthroughs (2-4 giờ tổng)

**⚠️ PHASE NÀY BẮT BUỘC — KHÔNG ĐƯỢC SKIP**

### 4.1 Mermaid .md diagrams — MỌI MODULE (không chỉ backend)
**Output:** `docs/architecture/{module}/diagrams/{flow}.md` per module

**Backend modules:** TỐI THIỂU 1 sequence diagram cho main API flow.
**Frontend modules:** TỐI THIỂU 1 navigation/route flow diagram.
**Mobile modules:** TỐI THIỂU 1 screen navigation flow diagram.

### 4.2 Entity lifecycle diagrams — PER-MODULE, KHÔNG consolidated
**Output:** `docs/architecture/{module}/diagrams/{entity}-lifecycle.md`

Mỗi lifecycle file PHẢI có:
- `stateDiagram-v2` Mermaid block
- **Transition Details table** (From, To, Trigger, Guard, Side Effects)
- **Partial Commit Risks table** (Risk, States Affected, Symptoms, Debug Query)
- **Transaction Boundaries** — annotate transition nào có @Transactional

### 4.3 Interactive Walkthroughs — ≥3 VÀ ≥1 PER MODULE CÓ USER-FACING FLOWS (v7)
**Output:** `docs/architecture/{module}/walkthroughs/{flow}-walkthrough.html`

⚠️ **v7 RULE — 2 điều kiện BẮT BUỘC:**
1. Tổng ≥3 walkthroughs cho core business flows
2. **MỌI module có user-facing flows** (backend có API endpoints, frontend có UI) PHẢI có ≥1 walkthrough

KHÔng được tập trung walkthroughs cho 1-2 modules rồi skip modules khác.

Pattern: Self-contained HTML, Mermaid CDN, dark theme (#1e1e1e), split layout (diagram left + info panel right 380px), clickable flowchart nodes.

⚠️ **Walkthrough anti-skip (v6+):** Mỗi walkthrough ~40-50KB HTML. Nếu context gần hết → `/compact` → tạo trong session mới. KHÔNG skip. Tạo 1 per session nếu cần.

**⚠️ COMPLETION GATE Phase 4 (v7 enhanced):**
```bash
echo "=== PHASE 4 GATE ==="

# 4.1 Diagrams per module
total_mod=0; has_diag=0
for dir in $(find docs/architecture -mindepth 1 -maxdepth 1 -type d \
  ! -name '_templates' ! -name 'cross-cutting-flows' ! -name 'dependency-map' \
  ! -name 'known-issues' ! -name 'glossary' ! -name 'field-mapping-ui'); do
  total_mod=$((total_mod+1))
  [ $(find "$dir" -path "*/diagrams/*.md" 2>/dev/null | wc -l) -ge 1 ] && has_diag=$((has_diag+1))
done
echo "Modules with diagrams: $has_diag/$total_mod (target: ALL)"
[ $has_diag -lt $total_mod ] && echo "❌ $(($total_mod - $has_diag)) modules THIẾU diagrams"

# 4.2 Lifecycle diagrams
lifecycle=$(find docs/architecture -path '*/diagrams/*lifecycle*' | wc -l)
echo "Lifecycle diagrams: $lifecycle (target: ≥3)"

# 4.3 Walkthroughs — total + per module (v7)
wt_total=$(find docs/architecture -path '*/walkthroughs/*.html' | wc -l)
echo "Walkthroughs total: $wt_total (target: ≥3)"

echo "--- Walkthrough per-module coverage (v7) ---"
for dir in $(find docs/architecture -mindepth 1 -maxdepth 1 -type d \
  ! -name '_templates' ! -name 'cross-cutting-flows' ! -name 'dependency-map' \
  ! -name 'known-issues' ! -name 'glossary' ! -name 'field-mapping-ui'); do
  mod=$(basename "$dir")
  wt=$(find "$dir" -path "*/walkthroughs/*.html" 2>/dev/null | wc -l)
  has_api=$([ -f "$dir/api-reference.md" ] || [ -f "$dir/code-structure.md" ] && echo "yes" || echo "no")
  if [ "$has_api" = "yes" ] && [ "$wt" -eq 0 ]; then
    echo "  ⚠️ $mod: user-facing module but 0 walkthroughs"
  fi
done

# 4.4 File size check for diagrams (v6)
find docs/architecture -path "*/diagrams/*.md" -exec sh -c \
  'sz=$(wc -c < "$1"); [ $sz -gt 15360 ] && echo "⚠️ >15KB: $1 → SPLIT"' _ {} \;
```

---

## Phase 5: Shared Docs (1-3 giờ tổng)

**⚠️ PHASE NÀY BẮT BUỘC — KHÔNG ĐƯỢC SKIP**

### 5.1 operational-runbook.md — 7 REQUIRED SECTIONS (v7 enhanced)
**Output:** `docs/architecture/operational-runbook.md` (dùng template)

⚠️ v7: PHẢI có TẤT CẢ 7 sections sau:

1. **Service Health Checks** — bảng Health URL + expected response per service
2. **Common Troubleshooting** — bảng Symptom → Cause → Check → Fix (≥8 rows)
3. **Log Grep Commands** — bảng Error Type → Grep Command → What to Look For
   - **≥5 grep commands per backend service**
   - Cover: business logic errors, integration failures, DB errors, auth errors, async/Kafka errors
4. **Kafka/MQ Monitoring** (nếu hệ thống dùng) — consumer lag check, topic health, DLQ monitoring
5. **External Integration Health** — health check commands cho mỗi external service (SFTP, SAP, eBao, etc.)
6. **Scheduled Job Monitoring** — verify job status, check last run, manual trigger commands
7. **Restart Order** — dependency-based restart sequence

### 5.2 thread-async-model.md + thread-async-risks.md (2 FILES BẮT BUỘC)
**Output:**
- `docs/architecture/thread-async-model.md` — Thread pools, consumer model, scheduled tasks
- `docs/architecture/thread-async-risks.md` — Event ordering, idempotency risks, anti-patterns

```bash
grep -rn "@Async\|@EventListener\|@KafkaListener\|@RabbitListener" {SOURCE_ROOT} --include="*.java"
grep -rn "ThreadPool\|Executor\|CompletableFuture\|@Scheduled" {SOURCE_ROOT} --include="*.java"
```

### 5.3 testing-patterns.md
**Output:** `docs/architecture/testing-patterns.md` (adapt template per tech stack)

### 5.4 Example Test Files (STEP RIÊNG — v3+)
⚠️ **PHẢI tạo ≥2 example test files** trong `_templates/`:
- `_templates/example-controller-test.java` (MockMvc, happy path, error cases, mock service)
- `_templates/example-service-test.java` (Mockito, business logic, exceptions, verify)
- (Optional) `_templates/example-widget-test.dart` nếu Flutter/mobile

Tìm real test files trong source → dùng làm base → anonymize + annotate.
Mỗi file ≤100 lines.

### 5.5 Update shared docs
- dependency-map: enrich với endpoints, tables, events
- glossary: thêm terms phát hiện trong Phase 2-3
- SUMMARY.md: update với module details
- READING-GUIDE.md: finalize navigation

---

## Phase 6: Integration Docs (2-4 giờ tổng) — v9 NEW

**⚠️ PHASE NÀY BẮT BUỘC khi hệ thống có external-facing APIs hoặc Kafka events cho bên ngoài.**

### Trigger Detection
```bash
# Chạy TRƯỚC Phase 6 để xác định có cần không
open_apis=$(find docs/architecture -path "*/open-*" -name "api-reference.md" | wc -l)
kafka_ext=$(grep -rli "KafkaListener\|kafka.*topic" docs/architecture/*/code-structure.md 2>/dev/null | wc -l)
echo "Open API modules: $open_apis"
echo "Kafka-enabled modules: $kafka_ext"
# Nếu open_apis > 0 HOẶC kafka_ext > 0 → Phase 6 BẮT BUỘC
```

### 6.1 API Catalog
**Output:** `docs/architecture/integration/api-catalog.md` (dùng template `_templates/api-catalog-template.md`)

Aggregate TẤT CẢ api-reference*.md → group by domain → 1 bảng tổng hợp.
Mỗi endpoint 1 row: Method, Path, Auth, Description, Side Effects, Link chi tiết.

### 6.2 Integration Guide
**Output:** `docs/architecture/integration/integration-guide.md` (dùng template)

8 sections BẮT BUỘC: Quick Start, Authentication, API Conventions, Error Handling Strategy, 
Common Integration Flows, Kafka Events, Environment URLs, FAQ.

### 6.3 Error Code Registry
**Output:** `docs/architecture/integration/error-code-registry.md` (dùng template)

Extract TẤT CẢ error codes từ error-handling.md + api-reference.md → searchable by code.

### 6.4 Kafka Event Catalog
**Output:** `docs/architecture/integration/kafka-event-catalog.md` (dùng template)

Scan @KafkaListener + kafkaTemplate.send → topic name, payload schema, producer, consumer, DLQ.

### 6.5 Environment Config
**Output:** `docs/architecture/integration/environment-config.md` (dùng template)

Aggregate environment URLs per service: dev, UAT, staging, prod.

### ⚠️ COMPLETION GATE Phase 6
```bash
echo "=== PHASE 6 GATE ==="
int_dir="docs/architecture/integration"
for f in api-catalog.md integration-guide.md error-code-registry.md kafka-event-catalog.md environment-config.md; do
  [ -f "$int_dir/$f" ] && echo "✅ $f" || echo "❌ $f MISSING"
done
# Guide must have 8 sections
sections=$(grep -c "^## " "$int_dir/integration-guide.md" 2>/dev/null || echo 0)
echo "Integration Guide sections: $sections (target: ≥8)"
# File size check
for f in "$int_dir"/*.md; do
  size=$(($(wc -c < "$f") / 1024))
  [ "$size" -gt 15 ] && echo "❌ >15KB: $(basename $f) → SPLIT"
done

---
## ⚠️ FINAL COMPLETION GATE v7 — Project CHƯA XONG nếu thiếu BẤT KỲ item nào

```bash
echo "=== MANDATORY SHARED DOCS ==="
[ -f docs/architecture/SUMMARY.md ]                    && echo "✅ SUMMARY.md" || echo "❌ SUMMARY.md"
[ -f docs/architecture/READING-GUIDE.md ]              && echo "✅ READING-GUIDE.md" || echo "❌ READING-GUIDE.md"
[ -f docs/architecture/error-patterns.md ]             && echo "✅ error-patterns.md" || echo "❌ error-patterns.md"
[ -f docs/architecture/operational-runbook.md ]         && echo "✅ operational-runbook.md" || echo "❌ operational-runbook.md"
[ -f docs/architecture/thread-async-model.md ]         && echo "✅ thread-async-model.md" || echo "❌ thread-async-model.md"
[ -f docs/architecture/thread-async-risks.md ]         && echo "✅ thread-async-risks.md" || echo "❌ thread-async-risks.md"
[ -f docs/architecture/testing-patterns.md ]           && echo "✅ testing-patterns.md" || echo "❌ testing-patterns.md"
[ -d docs/architecture/cross-cutting-flows ] && [ $(find docs/architecture/cross-cutting-flows -name "*.md" | wc -l) -ge 3 ] && echo "✅ cross-cutting-flows/" || echo "❌ cross-cutting-flows/ (<3 files)"
[ -d docs/architecture/known-issues ] && [ $(find docs/architecture/known-issues -name "*.md" | wc -l) -ge 2 ] && echo "✅ known-issues/" || echo "❌ known-issues/"
[ -d docs/architecture/glossary ] && [ $(find docs/architecture/glossary -name "*.md" | wc -l) -ge 2 ] && echo "✅ glossary/" || echo "❌ glossary/"
[ -d docs/architecture/dependency-map ]                && echo "✅ dependency-map/" || echo "❌ dependency-map/"

echo "=== DIAGRAMS (MỌI module) ==="
total_mod=0; has_diag=0
for dir in $(find docs/architecture -mindepth 1 -maxdepth 1 -type d \
  ! -name '_templates' ! -name 'cross-cutting-flows' ! -name 'dependency-map' \
  ! -name 'known-issues' ! -name 'glossary' ! -name 'field-mapping-ui'); do
  total_mod=$((total_mod+1))
  [ $(find "$dir" -path "*/diagrams/*.md" 2>/dev/null | wc -l) -ge 1 ] && has_diag=$((has_diag+1))
done
echo "Modules with diagrams: $has_diag/$total_mod (target: ALL modules)"
[ $has_diag -lt $total_mod ] && echo "❌ $(($total_mod - $has_diag)) modules THIẾU diagrams"

echo "Lifecycle diagrams: $(find docs/architecture -path '*/diagrams/*lifecycle*' | wc -l) (target: ≥3)"
echo "Walkthroughs: $(find docs/architecture -path '*/walkthroughs/*.html' | wc -l) (target: ≥3)"

echo "=== FILE SIZE ==="
over15=$(find docs/architecture -name "*.md" ! -path "*_templates*" -exec sh -c 'test $(wc -c < "$1") -gt 15360 && echo "$1"' _ {} \; | wc -l)
echo "Files >15KB: $over15 (target: 0, acceptable: ≤3 nếu coherent content)"

echo "=== DEBUG & TEST READINESS ==="
debug_ep=0
for f in $(find docs/architecture -name "code-structure.md" ! -path "*_templates*"); do
  grep -qi "debug.*entry\|entry.*point" "$f" 2>/dev/null && debug_ep=$((debug_ep+1))
done
echo "Modules with Debug Entry Points: $debug_ep (target: ≥80% backend modules)"

config_impact=0
for f in $(find docs/architecture -name "configuration.md" ! -path "*_templates*"); do
  grep -qi "impact.*wrong\|impact.*sai" "$f" 2>/dev/null && config_impact=$((config_impact+1))
done
echo "Modules with Config Impact column: $config_impact (target: ≥80% backend modules)"

test_examples=$(find docs/architecture/_templates -name "example-*test*" 2>/dev/null | wc -l)
echo "Example test files: $test_examples (target: ≥2)"

echo "=== V7 CHECKS (GL-Transform lessons) ==="

# v7 Check 1: Walkthrough per-module coverage
echo "--- Walkthrough per-module ---"
for dir in $(find docs/architecture -mindepth 1 -maxdepth 1 -type d \
  ! -name '_templates' ! -name 'cross-cutting-flows' ! -name 'dependency-map' \
  ! -name 'known-issues' ! -name 'glossary' ! -name 'field-mapping-ui'); do
  mod=$(basename "$dir")
  wt=$(find "$dir" -path "*/walkthroughs/*.html" 2>/dev/null | wc -l)
  has_api=$([ -f "$dir/api-reference.md" ] && echo "yes" || echo "no")
  [ "$has_api" = "yes" ] && [ "$wt" -eq 0 ] && echo "  ⚠️ $mod: API module, 0 walkthroughs"
done

# v7 Check 2: cURL per-module balance
echo "--- cURL per-module ---"
for f in $(find docs/architecture -name "api-reference.md" ! -path "*_templates*"); do
  mod=$(echo "$f" | sed 's|docs/architecture/||;s|/api-reference.md||')
  curls=$(grep -c "curl " "$f" 2>/dev/null || echo 0)
  [ "$curls" -lt 2 ] && echo "  ⚠️ $mod: only $curls cURL examples"
done

# v7 Check 3: Runbook depth
echo "--- Runbook depth ---"
if [ -f docs/architecture/operational-runbook.md ]; then
  sections=$(grep -c "^## " docs/architecture/operational-runbook.md)
  greps=$(grep -c "grep " docs/architecture/operational-runbook.md)
  [ "$sections" -lt 7 ] && echo "  ⚠️ Runbook: $sections sections (target ≥7)"
  [ "$greps" -lt 15 ] && echo "  ⚠️ Runbook: $greps grep commands (target ≥15)"
  echo "  Runbook: $sections sections, $greps grep commands"
fi

# v7 Check 4: Data format docs for file-processing modules
echo "--- Data format docs (v7) ---"
for dir in $(find docs/architecture -mindepth 1 -maxdepth 1 -type d \
  ! -name '_templates' ! -name 'cross-cutting-flows' ! -name 'dependency-map' \
  ! -name 'known-issues' ! -name 'glossary' ! -name 'field-mapping-ui'); do
  mod=$(basename "$dir")
  if [ -f "$dir/data-formats.md" ]; then
    echo "  ✅ $mod: data-formats.md exists"
  fi
done
```

---

## Quality Targets

| Metric | Target | Measured by |
|--------|--------|------------|
| YAML frontmatter | 100% | head -1 every .md |
| Impact tags | ≥10 per backend module | grep count |
| cURL examples | ≥1 per endpoint, **balanced per module (v7)** | grep per api-reference.md |
| Canonical refs | ≥3 per error-handling.md | grep count |
| **Debug Entry Points (v8)** | **100% code-structure.md, ≥3 rows each** | **grep "Debug Entry Point" per file** |
| **Side Effects (v8)** | **100% api-reference.md** | **grep -i "side effect" per file** |
| Mermaid diagrams | ≥1 per module (**ALL**, not just backend) | find per module |
| Lifecycle diagrams | ≥3, **per-module** (NOT consolidated) | find */diagrams/*lifecycle* |
| Walkthroughs | ≥3 total **AND ≥1 per module with user-facing flows (v7)** | find */walkthroughs/*.html |
| Cross-cutting flows | ≥3 domain files | find count |
| Known issues | ≥5 per domain | grep count |
| Runbook sections | **≥7 (v7)** | grep "^## " count |
| Runbook grep commands | **≥15 (v7)** | grep "grep " count |
| **Files ≤ 15KB (v8)** | **100% (0 files >15KB)** | **verify-docs.sh — HARD FAIL if >0** |
| Shared docs | ALL mandatory items | Final Completion Gate |
| Data format docs | **ALL file-processing modules (v7)** | per-module check |
| **Field schemas (v9)** | **100% write endpoints have field table** | **grep "Field.*Type.*Required" per api-reference** |
| **Required/Optional (v9)** | **100% field tables have ✅/❌ markers** | **grep "✅\|❌" per field table** |
| **Enum values (v9)** | **100% enum fields have values listed** | **grep "Enum" + inline values** |

## Verification Script (v8)
```bash
# THAY verify-docs.sh cũ bằng v8 — checks ALL targets above
bash scripts/verify-docs.sh
# Target: 0 FAIL, ≤2 WARN → "🎯 TARGET MET: Ready for RE 99 / BF 98"
```

## Model Routing
- Phase 1 Discovery: **Opus**
- Phase 2 Core docs: **Sonnet** (subagent parallel per module)
- Phase 3 Error/Flows: **Opus** (cross-module analysis) then **Sonnet** (writing)
- Phase 4 Diagrams: **Sonnet**
- Phase 5 Shared docs: **Sonnet**


```
