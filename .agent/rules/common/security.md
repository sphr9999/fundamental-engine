---
trigger: always
description: "15 quy tắc bảo mật chung — áp dụng cho MỌI code trong LAMB Platform"
globs: ["**"]
---

# Security Guidelines — 15 Quy Tắc Vàng

> Áp dụng cho MỌI code trong dự án. Dựa trên OWASP Top 10:2025, CWE/SANS Top 25:2024.

## Phương Pháp Phân Tích

Khi viết code liên quan đến user input, authentication, hoặc data access:
1. **Truy vết data flow**: Input → xử lý → sink
2. **Đánh giá exploitability**: Input có do attacker kiểm soát không?
3. **Phân biệt context**: `request.getParameter()` = NGUY HIỂM vs `System.getenv()` = AN TOÀN
4. **Defense-in-depth**: Không dựa vào một lớp phòng thủ duy nhất

## 15 Quy Tắc — LUÔN TUÂN THỦ

### 1. NEVER hardcode secrets
API keys, passwords, tokens — LUÔN dùng environment variables hoặc secret manager.

### 2. ALWAYS validate input server-side
Client-side validation chỉ là UX. Server PHẢI validate lại toàn bộ.

### 3. ALWAYS dùng parameterized queries
KHÔNG BAO GIỜ nối chuỗi SQL, NoSQL, LDAP với user input.

### 4. ALWAYS enforce auth + authz trên mọi endpoint
Deny by default. Kiểm tra quyền tại tầng data access, KHÔNG chỉ middleware.

### 5. NEVER log dữ liệu nhạy cảm
Passwords, tokens, credit cards, PII, CCCD — mask trước khi log.

```
# ❌ NEVER
log.info("User login: password={}", password);

# ✅ ALWAYS
log.info("User login: email={}", maskEmail(email));
```

### 6. ALWAYS dùng HTTPS
TLS 1.2+ bắt buộc trong production.

### 7. ALWAYS set security headers
CSP, X-Frame-Options: DENY, X-Content-Type-Options: nosniff, HSTS, Referrer-Policy.

### 8. ALWAYS dùng DTO/Response models
KHÔNG BAO GIỜ trả entity trực tiếp qua API.

### 9. NEVER dùng eval(), new Function() với user input

### 10. ALWAYS pin dependency versions
Java: version cụ thể trong pom.xml. Node: `save-exact=true`. Flutter: version cụ thể.

### 11. ALWAYS dùng bcrypt/Argon2 cho passwords
Cost factor >= 12 cho bcrypt. NEVER MD5 hoặc SHA cho passwords.

### 12. ALWAYS dùng SecureRandom
NEVER `java.util.Random`, `Math.random()` cho tokens, keys, giá trị bảo mật.

### 13. NEVER expose stack traces cho client
Log chi tiết server-side, trả generic message cho client.

### 14. ALWAYS implement rate limiting
Bắt buộc trên: auth endpoints, file uploads, password reset.

### 15. ALWAYS review dependencies cho CVE
Tích hợp dependency scanning trong CI/CD.

## Mối Đe Dọa Hiện Đại

### SSRF
```
# ❌ NEVER: Fetch URL trực tiếp từ user input
response = httpClient.get(userProvidedUrl);

# ✅ ALWAYS: Whitelist domains + validate URL
if (!ALLOWED_HOSTS.contains(url.getHost())) throw new SecurityException();
if (isPrivateIP(url)) throw new SecurityException();
```

### Mass Assignment
```
# ❌ NEVER: Bind toàn bộ request body vào entity
userRepository.save(objectMapper.readValue(requestBody, User.class));

# ✅ ALWAYS: Dùng DTO với chỉ các fields cho phép
```

## .gitignore BẮT BUỘC

```
*.env
*.env.local
application-local.yml
application-secret.yml
**/secrets/**
*.pem *.key *.p12 *.jks
id_rsa*
```

## False Positive Guidance

- `eval()` với server-generated constant → SAFE
- `MD5` cho file checksum → SAFE (chỉ flag khi dùng cho password)
- String concat trong log message → SAFE (chỉ flag khi SQL)
- `http://` trong localhost dev → SAFE

## References

- Java-specific: `java/security.md`
- Full detailed version: `ECC/.claude/rules/security-general.md`
