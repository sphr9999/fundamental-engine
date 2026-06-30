---
trigger: always
description: "Source directory mapping cho 21 modules LAMB — AI Agent dùng để locate source code"
globs: ["lamb-backend/**", "eform-service/**", "lamb-admin-fe/**", "mbal-mobile-app/**"]
---

# Source Directory Mapping — LAMB Platform

> Workspace root: `AI-lamb/` (git superproject chứa submodules).
> Paths bên dưới là relative từ workspace root.

## lamb-backend (core cluster) — submodule `lamb-backend/`

| Module | Source Directory | Notes |
|--------|----------------|-------|
| user-service | `lamb-backend/user-service/` | `*-interface` + `*-impl` layers |
| policy-service | `lamb-backend/policy-service/` | `*-interface` + `*-impl` |
| payment-service | `lamb-backend/payment-service/` | `*-interface` + `*-impl` |
| admin-service | `lamb-backend/admin-service/` | `*-interface` + `*-impl` |
| eform-core | `lamb-backend/eform-core/` | `*-interface` + `*-impl` |
| eform-service (lamb) | `lamb-backend/eform-service/` | ⚠️ TÊN TRÙNG với CP cluster |
| account-management | `lamb-backend/account-management/` | Dùng chung DB `user_service` |
| notification-service | `lamb-backend/notification-service/` | `*-interface` + `*-impl` |
| common (lamb) | `lamb-backend/common/` | Shared Java lib |
| database-migration (lamb) | `lamb-backend/database-migration/` | Liquibase |

## eform-service / CP cluster — submodule `eform-service/`

| Module | Source Directory | Notes |
|--------|----------------|-------|
| eform-service gateway (CP) | `eform-service/eform-service/` | Feign gateway; ⚠️ TÊN TRÙNG |
| claim-service | `eform-service/claim-service/` | artifactId prefix `cp-` |
| pcm-service | `eform-service/pcm-service/` | artifactId prefix `cp-` |
| refund-service | `eform-service/refund-service/` | artifactId prefix `cp-` |
| common (CP) | `eform-service/common/` | Shared Java lib CP |
| database-migration (CP) | `eform-service/database-migration/` | Liquibase CP |

## Frontends / Mobile — submodules

| Module | Source Directory | Notes |
|--------|----------------|-------|
| lamb-admin-fe | `lamb-admin-fe/src/` | React 17, CRA, Redux, Ant Design |
| mbal-mobile-app | `mbal-mobile-app/lib/` | Flutter 3, Stacked + Bloc |

## ⚠️ Gotchas

- **Trùng tên `eform-service`**: `lamb-backend/eform-service/` (core) vs `eform-service/eform-service/` (CP gateway)
- **Trùng tên `common`**: mỗi cụm có bản riêng
- **Trùng tên `database-migration`**: mỗi cụm có bản riêng
- **account-management** dùng chung DB `user_service` — ngoại lệ database-per-service
