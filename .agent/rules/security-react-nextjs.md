# Security Rules — React 18+ / Next.js

Áp dụng khi viết code frontend trong bất kỳ app React/Next.js nào (12 apps).
Stack: React 18+, Next.js, TypeScript.

## XSS PREVENTION — CWE-79

```tsx
// ❌ NEVER: dangerouslySetInnerHTML không sanitize
<div dangerouslySetInnerHTML={{ __html: userContent }} />

// ❌ NEVER: href với URL không validate
<a href={userProvidedUrl}>Click</a> // javascript:alert(1)

// ❌ NEVER: eval hoặc new Function với user input
const result = eval(userExpression);
const fn = new Function('return ' + userInput);

// ❌ NEVER: innerHTML qua ref
useEffect(() => { divRef.current.innerHTML = userContent; }, []);
```

```tsx
// ✅ ALWAYS: DOMPurify sanitize
import DOMPurify from 'isomorphic-dompurify';

function SafeHtml({ content }: { content: string }) {
  const clean = DOMPurify.sanitize(content, {
    ALLOWED_TAGS: ['p', 'br', 'strong', 'em', 'ul', 'ol', 'li', 'a', 'h1', 'h2', 'h3'],
    ALLOWED_ATTR: ['href', 'target', 'rel'],
    ALLOW_DATA_ATTR: false,
  });
  return <div dangerouslySetInnerHTML={{ __html: clean }} />;
}

// ✅ BETTER: ReactMarkdown thay raw HTML
import ReactMarkdown from 'react-markdown';
function Post({ md }: { md: string }) { return <ReactMarkdown>{md}</ReactMarkdown>; }

// ✅ ALWAYS: Validate URL protocol
function SafeLink({ href, children }: { href: string; children: React.ReactNode }) {
  const safeUrl = (url: string): string => {
    try {
      const parsed = new URL(url);
      return ['http:', 'https:'].includes(parsed.protocol) ? parsed.href : '#';
    } catch { return '#'; }
  };
  return <a href={safeUrl(href)} target="_blank" rel="noopener noreferrer">{children}</a>;
}
```

## TOKEN STORAGE — KHÔNG localStorage cho sensitive tokens

```tsx
// ❌ NEVER: JWT trong localStorage hoặc sessionStorage
localStorage.setItem('accessToken', token);  // XSS → steal token
sessionStorage.setItem('token', jwt);

// ✅ ALWAYS: Access token trong memory + Refresh token httpOnly cookie
class TokenManager {
  private accessToken: string | null = null;
  setToken(token: string) { this.accessToken = token; }
  getToken() { return this.accessToken; }
  clearToken() { this.accessToken = null; }
}
export const tokenManager = new TokenManager();
```

Server-side refresh token:
```tsx
// app/api/auth/login/route.ts
export async function POST(req: Request) {
  const { accessToken, refreshToken } = await authenticate(credentials);
  const response = NextResponse.json({ accessToken });
  response.cookies.set('refreshToken', refreshToken, {
    httpOnly: true,     // JS không đọc được
    secure: true,       // HTTPS only
    sameSite: 'strict', // Chống CSRF
    path: '/api/auth/refresh',
    maxAge: 7 * 24 * 60 * 60,
  });
  return response;
}
```

Axios interceptor:
```tsx
// ✅ ALWAYS: Lấy token từ memory
api.interceptors.request.use((config) => {
  const token = tokenManager.getToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});
```

## ENVIRONMENT VARIABLES — NEXT_PUBLIC_ awareness

```env
# ❌ NEVER: Secret với NEXT_PUBLIC_ prefix (inline vào JS bundle!)
NEXT_PUBLIC_DATABASE_URL=postgres://user:password@host/db
NEXT_PUBLIC_API_SECRET=sk_live_1234567890
NEXT_PUBLIC_JWT_SECRET=super-secret-key

# ✅ ALWAYS: Phân tách rõ ràng
# Server-only (KHÔNG có NEXT_PUBLIC_)
DATABASE_URL=postgres://user:password@host/db
AUTH_SECRET=your-signing-secret
STRIPE_SECRET_KEY=sk_live_...

# Intentionally public
NEXT_PUBLIC_APP_URL=https://yourapp.com
NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY=pk_live_...
```

```tsx
// ✅ ALWAYS: Validate env lúc build time
import 'server-only';
import { z } from 'zod';

const serverEnvSchema = z.object({
  DATABASE_URL: z.string().url(),
  JWT_SECRET: z.string().min(32),
});
export const serverEnv = serverEnvSchema.parse(process.env);
```

## API ROUTE & SERVER ACTION SECURITY

**Middleware KHÔNG phải security boundary** — CVE-2025-29927 bypass toàn bộ middleware auth.

```tsx
// ❌ NEVER: API route không auth
export async function GET() {
  return Response.json(await db.users.findMany()); // Public!
}

// ❌ NEVER: Chỉ dựa vào middleware cho auth
```

```tsx
// ✅ ALWAYS: Auth + Authz + Validation trong MỌI handler
export async function GET() {
  const session = await getSession();
  if (!session) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
  if (session.role !== 'admin')
    return NextResponse.json({ error: 'Forbidden' }, { status: 403 });
  const users = await db.users.findMany({
    select: { id: true, name: true, email: true } // Chỉ fields cần thiết
  });
  return NextResponse.json(users);
}

// ✅ ALWAYS: Zod validation trong Server Actions
'use server';
import { z } from 'zod';

const Schema = z.object({
  displayName: z.string().min(1).max(64),
  email: z.string().email(),
});

export async function updateProfile(formData: FormData) {
  const session = await getSession();
  if (!session) throw new Error('Unauthorized');
  const parsed = Schema.safeParse({
    displayName: formData.get('displayName'),
    email: formData.get('email'),
  });
  if (!parsed.success) return { error: 'Invalid input' };
  await db.users.update({ where: { id: session.userId }, data: parsed.data });
}

// ✅ ALWAYS: Re-verify auth trong Server Components
import { cookies } from 'next/headers';
export default async function DashboardPage() {
  const session = await verifySession((await cookies()).get('session')?.value);
  if (!session) redirect('/login');
  return <Dashboard data={await getAuthorizedData(session.userId)} />;
}
```

## CSP — Content Security Policy

```tsx
// ✅ ALWAYS: Nonce-based CSP qua Middleware
// middleware.ts
export function middleware(request: NextRequest) {
  const nonce = Buffer.from(crypto.randomUUID()).toString('base64');
  const isDev = process.env.NODE_ENV === 'development';
  const csp = `
    default-src 'self';
    script-src 'self' 'nonce-${nonce}' 'strict-dynamic'${isDev ? " 'unsafe-eval'" : ''};
    style-src 'self' 'nonce-${nonce}';
    img-src 'self' blob: data:;
    font-src 'self';
    object-src 'none';
    base-uri 'self';
    form-action 'self';
    frame-ancestors 'none';
    upgrade-insecure-requests;
  `.replace(/\s{2,}/g, ' ').trim();
  const headers = new Headers(request.headers);
  headers.set('x-nonce', nonce);
  headers.set('Content-Security-Policy', csp);
  const response = NextResponse.next({ request: { headers } });
  response.headers.set('Content-Security-Policy', csp);
  return response;
}
```

## SECURITY HEADERS — next.config.ts

```ts
// ✅ ALWAYS
const securityHeaders = [
  { key: 'X-Frame-Options', value: 'DENY' },
  { key: 'X-Content-Type-Options', value: 'nosniff' },
  { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
  { key: 'Permissions-Policy', value: 'camera=(), microphone=(), geolocation=()' },
  { key: 'Strict-Transport-Security', value: 'max-age=63072000; includeSubDomains; preload' },
];
const nextConfig = {
  async headers() {
    return [{ source: '/(.*)', headers: securityHeaders }];
  },
  productionBrowserSourceMaps: false,
};
```

## URL/REDIRECT VALIDATION — Chống Open Redirect

```tsx
// ❌ NEVER
router.push(searchParams.get('redirect') || '/dashboard');

// ✅ ALWAYS: Validate relative path
function getSafeRedirect(returnTo: string | null): string {
  if (!returnTo || typeof returnTo !== 'string' ||
      !returnTo.startsWith('/') || returnTo.startsWith('//') ||
      returnTo.includes('\\') || /^\/[a-z]+:/i.test(returnTo))
    return '/dashboard';
  return returnTo;
}
```

## SECURE COOKIES

```tsx
// ✅ ALWAYS: Đầy đủ security flags
import { cookies } from 'next/headers';

export async function setSessionCookie(token: string) {
  (await cookies()).set('session', token, {
    httpOnly: true,
    secure: true,
    sameSite: 'lax',
    path: '/',
    maxAge: 60 * 60 * 24,
  });
}
```

## NPM DEPENDENCY SECURITY

```ini
# ✅ ALWAYS: .npmrc hardening
engine-strict=true
ignore-scripts=true
package-lock=true
save-exact=true
audit=true
```

```yaml
# ✅ ALWAYS: CI pipeline
- run: npm ci              # Enforce lockfile
- run: npm audit --audit-level=high
- run: npm audit signatures
```
