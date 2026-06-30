---
trigger: always
description: "Java Spring Boot security rules cho LAMB Platform — Java 11 / SB 2.6.7 / SS 5.x"
globs: ["**/*.java", "**/application*.yml", "**/application*.yaml"]
---

# Java Security — LAMB Platform

> **Stack chính**: Java 11, Spring Boot 2.6.7, Spring Security 5.x.
> **Ngoại lệ**: dynamic-link = Java 21, Spring Boot 3.2.5, Spring Security 6.x.
> File này extends [common/security.md](../common/security.md) với Java-specific rules.

## SQL INJECTION — CWE-89

```java
// ❌ NEVER: Nối chuỗi SQL
@Query(value = "SELECT * FROM users WHERE name = '" + name + "'", nativeQuery = true)
String sql = String.format("DELETE FROM orders WHERE id = %s", orderId);
entityManager.createNativeQuery("SELECT * FROM t WHERE col = '" + input + "'");

// ✅ ALWAYS: Parameterized queries
@Query("SELECT u FROM User u WHERE u.name = :name")
List<User> findByName(@Param("name") String name);

@Query(value = "SELECT * FROM users WHERE email = ?1", nativeQuery = true)
User findByEmail(String email);

// ✅ BEST: Derived queries (tự động an toàn)
List<User> findByEmailAndStatus(String email, String status);

// ✅ BEST: Specification cho dynamic queries
public static Specification<User> hasName(String name) {
    return (root, query, cb) -> cb.equal(root.get("name"), name);
}
```

## AUTHENTICATION & AUTHORIZATION — Spring Security 5.x

```java
// ❌ NEVER
http.authorizeRequests().anyRequest().permitAll();

// ❌ NEVER: Tự parse JWT không verify signature
String payload = new String(Base64.decode(token.split("\\.")[1]));

// ❌ NEVER: Hardcode credentials
String adminPassword = "admin123";
```

```java
// ✅ ALWAYS: Spring Security 5.x configuration (SB 2.6.7)
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .authorizeRequests()
                .antMatchers("/api/public/**").permitAll()
                .antMatchers("/api/admin/**").hasRole("ADMIN")
                .antMatchers("/actuator/health").permitAll()
                .antMatchers("/actuator/**").hasRole("ADMIN")
                .anyRequest().authenticated()  // Deny by default
            .and()
            .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .oauth2ResourceServer()
                .jwt();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12); // Cost >= 12
    }
}

// ✅ ALWAYS: Method-level authorization
@PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.id")
public UserDto getUser(@PathVariable Long userId) { ... }
```

## INPUT VALIDATION — CWE-20

```java
// ❌ NEVER: Controller không validate
@PostMapping("/users")
public ResponseEntity<?> createUser(@RequestBody UserRequest req) {
    userService.create(req); // Không validate!
}

// ✅ ALWAYS: @Valid + Bean Validation (Java 11 dùng javax.validation)
public class UserRequest {
    @NotBlank @Size(min = 2, max = 50)
    private String name;

    @NotBlank @Email
    private String email;

    @NotNull @Min(0) @Max(150)
    private Integer age;
}

@PostMapping("/users")
public ResponseEntity<?> createUser(@Valid @RequestBody UserRequest req) {
    userService.create(req);
}
```

## GLOBAL EXCEPTION HANDLER

```java
// ✅ ALWAYS: Không expose stack traces
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                e -> e.getDefaultMessage() != null ? e.getDefaultMessage() : "Invalid",
                (a, b) -> a));
        return ResponseEntity.badRequest().body(errors);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.internalServerError()
            .body(Map.of("error", "Đã xảy ra lỗi"));
    }
}
```

> ⚠️ **LAMB-specific**: Nhiều service dùng `BizException` → trả HTTP 200 + `responseCode` body
> thay vì HTTP error status. Đây là platform convention, PHẢI follow cho consistency.

## JACKSON SERIALIZATION — CWE-502

```java
// ❌ NEVER: Enable default typing
mapper.enableDefaultTyping(); // RCE vulnerability!

// ❌ NEVER: Trả entity qua API
@GetMapping("/users/{id}")
public User getUser(@PathVariable Long id) { return userRepo.findById(id).orElseThrow(); }

// ✅ ALWAYS: DTO thay entity
@GetMapping("/users/{id}")
public UserResponseDto getUser(@PathVariable Long id) {
    User user = userRepo.findById(id).orElseThrow();
    return new UserResponseDto(user.getId(), user.getName(), user.getEmail());
}
```

## LOGGING SECURITY — CWE-200

```java
// ❌ NEVER
log.info("Login: email={}, password={}", email, password);
log.debug("JWT: {}", jwtToken);

// ✅ ALWAYS: Mask sensitive data
log.info("Login: email={}", maskEmail(email));

// ✅ ALWAYS: @ToString.Exclude cho sensitive fields
@Data
public class UserDto {
    private String name;
    @ToString.Exclude private String password;
    @ToString.Exclude private String identityNumber;
}
```

> ⚠️ **LAMB-specific**: 15+ known issues về PII logging + hardcoded secrets trong YAML.
> Xem `docs/architecture/known-issues/`.

## SECRETS — application.yml

```yaml
# ❌ NEVER: Hardcode
spring.datasource.password: MyS3cretP@ss!

# ✅ ALWAYS: Environment variables
spring:
  datasource:
    password: ${DB_PASSWORD}
```

## CRYPTOGRAPHY

```java
// ❌ NEVER cho passwords
DigestUtils.md5DigestAsHex(password.getBytes());

// ❌ NEVER
Random random = new Random(); // Predictable!

// ✅ ALWAYS
@Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
SecureRandom secureRandom = new SecureRandom();
```

## KIẾN TRÚC TÍCH HỢP — Constraint cứng LAMB

**LAMB KHÔNG gọi trực tiếp Core eBao.**
Luồng bắt buộc: **LAMB → Digital Platform (API Gateway) → Core eBao**
Vi phạm constraint này là lỗi kiến trúc nghiêm trọng.

## References

- Full detailed version: `ECC/.claude/rules/security-java-spring.md`
- Skill: `springboot-security`
- Common security: `../common/security.md`
