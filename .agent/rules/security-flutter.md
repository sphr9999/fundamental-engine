# Security Rules — Flutter Mobile App

Áp dụng khi viết code Flutter. Dựa trên OWASP Mobile Top 10:2024.

## SECURE STORAGE — OWASP M9

```dart
// ❌ NEVER: SharedPreferences cho data nhạy cảm (plaintext!)
final prefs = await SharedPreferences.getInstance();
await prefs.setString('auth_token', token);
await prefs.setString('user_password', password); // TUYỆT ĐỐI KHÔNG

// ❌ NEVER: Lưu plaintext trên disk
final file = File('${directory.path}/card_data.txt');
await file.writeAsString(cardNumber);
```

```dart
// ✅ ALWAYS: flutter_secure_storage (Android Keystore / iOS Keychain)
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class SecureStorageService {
  static const _storage = FlutterSecureStorage(
    aOptions: AndroidOptions(encryptedSharedPreferences: true),
    iOptions: IOSOptions(
      accessibility: KeychainAccessibility.first_unlock_this_device,
    ),
  );

  Future<void> saveToken(String token) =>
      _storage.write(key: 'auth_token', value: token);
  Future<String?> getToken() => _storage.read(key: 'auth_token');
  Future<void> clearAll() => _storage.deleteAll();
}
```

```xml
<!-- ✅ ALWAYS: AndroidManifest.xml — tắt backup -->
<application android:allowBackup="false" android:fullBackupContent="false">
```

## CERTIFICATE PINNING — OWASP M5

```dart
// ❌ NEVER: HTTP client mặc định (tin mọi CA)
final response = await http.get(Uri.parse('https://api.example.com/data'));
```

```dart
// ✅ ALWAYS: SSL Pinning
import 'dart:io';
import 'package:flutter/services.dart';
import 'package:http/io_client.dart';

class SSLPinningService {
  static Future<IOClient> createPinnedClient() async {
    final sslCert = await rootBundle.load('assets/certs/server_cert.pem');
    final ctx = SecurityContext(withTrustedRoots: false);
    ctx.setTrustedCertificatesBytes(sslCert.buffer.asInt8List());
    final httpClient = HttpClient(context: ctx);
    httpClient.badCertificateCallback =
        (X509Certificate cert, String host, int port) => false;
    return IOClient(httpClient);
  }
}

// Usage:
final client = await SSLPinningService.createPinnedClient();
try {
  final response = await client.get(Uri.parse('https://api.example.com/data'));
} on HandshakeException {
  // SSL Pinning failed — có thể bị MITM
}
```

Luôn duy trì ít nhất **một backup pin** cho việc thay đổi chứng chỉ.

## ROOT/JAILBREAK DETECTION — OWASP M8

```dart
// ❌ NEVER: App chạy không kiểm tra thiết bị
void main() { runApp(MyApp()); }
```

```dart
// ✅ ALWAYS: freeRASP detection
import 'package:freerasp/freerasp.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  final config = TalsecConfig(
    androidConfig: AndroidConfig(
      packageName: 'com.example.app',
      signingCertHashes: ['YOUR_CERT_HASH'],
    ),
    iosConfig: IOSConfig(
      bundleIds: ['com.example.app'],
      teamId: 'YOUR_TEAM_ID',
    ),
    watcherMail: 'security@example.com',
  );

  final callback = ThreatCallback(
    onRootDetected: () => _handleThreat('Root detected'),
    onDebuggerDetected: () => _handleThreat('Debugger detected'),
    onTamperDetected: () => _handleThreat('App tampered'),
    onHookDetected: () => _handleThreat('Hook detected'),
  );

  await Talsec.instance.start(config, callback);
  runApp(const MyApp());
}

void _handleThreat(String threat) {
  // Log, restrict functionality, hoặc force logout
}
```

Root/jailbreak detection có thể bị bypass (Frida) — đây là một layer, không phải phòng thủ duy nhất.

## SECURE API COMMUNICATION

```dart
// ❌ NEVER
const String apiKey = 'sk-1234567890abcdef'; // Hardcode!
await http.post(Uri.parse('http://api.example.com/login')); // HTTP!
print('Response: ${response.body}'); // Log token!
```

```dart
// ✅ ALWAYS: HTTPS + Secure token management
import 'package:dio/dio.dart';

class SecureApiService {
  final _storage = const FlutterSecureStorage();
  late final Dio _dio;

  SecureApiService() {
    _dio = Dio(BaseOptions(
      baseUrl: 'https://api.example.com',
      connectTimeout: const Duration(seconds: 15),
      receiveTimeout: const Duration(seconds: 15),
    ));
    _dio.interceptors.add(InterceptorsWrapper(
      onRequest: (options, handler) async {
        final token = await _storage.read(key: 'auth_token');
        if (token != null) options.headers['Authorization'] = 'Bearer $token';
        handler.next(options);
      },
      onError: (error, handler) async {
        if (error.response?.statusCode == 401) {
          // Token refresh logic
        }
        handler.next(error);
      },
    ));
  }
}
```

```xml
<!-- ✅ ALWAYS: Android cấm cleartext -->
<!-- android/app/src/main/res/xml/network_security_config.xml -->
<network-security-config>
  <base-config cleartextTrafficPermitted="false">
    <trust-anchors><certificates src="system" /></trust-anchors>
  </base-config>
</network-security-config>
```

```dart
// ✅ ALWAYS: Build-time injection cho API keys
// flutter build apk --dart-define=API_KEY=xxx
const apiKey = String.fromEnvironment('API_KEY');
```

## OBFUSCATION — OWASP M7

```bash
# ❌ NEVER: Build không obfuscation
flutter build apk

# ✅ ALWAYS: Obfuscate + split debug info
flutter build apk --obfuscate --split-debug-info=./debug_symbols/
flutter build ipa --obfuscate --split-debug-info=./debug_symbols/
# Lưu debug_symbols/ an toàn cho crash symbolication
```

```dart
// ✅ ALWAYS: envied cho compile-time secret obfuscation
import 'package:envied/envied.dart';
part 'env.g.dart';

@Envied(path: '.env', obfuscate: true)
abstract class Env {
  @EnviedField(varName: 'API_KEY', obfuscate: true)
  static final String apiKey = _Env.apiKey;
}
```

## DATA, MEMORY & CLIPBOARD SECURITY

```dart
// ✅ ALWAYS: Mã hóa data nhạy cảm trên disk
import 'package:encrypt/encrypt.dart' as enc;

final key = enc.Key.fromSecureRandom(32);
final iv = enc.IV.fromSecureRandom(16);
final encrypter = enc.Encrypter(enc.AES(key, mode: enc.AESMode.cbc));
final encrypted = encrypter.encrypt(plaintext, iv: iv);

// ✅ ALWAYS: Tắt copy/paste trên sensitive fields
TextFormField(
  obscureText: true,
  enableInteractiveSelection: false,
  enableSuggestions: false,
  autocorrect: false,
  contextMenuBuilder: (context, state) => const SizedBox.shrink(),
)

// ✅ ALWAYS: Clear controller ngay sau khi dùng
authenticateUser(_passwordController.text);
_passwordController.clear();
```

## SECURE DEEP LINKING

```dart
// ❌ NEVER: Thực thi trực tiếp từ deep link params
void handleDeepLink(Uri uri) {
  transferMoney(double.parse(uri.queryParameters['amount']!)); // Nguy hiểm!
}

// ✅ ALWAYS: Validate + sanitize + re-authenticate
final id = state.pathParameters['id'];
if (id == null || !RegExp(r'^[a-zA-Z0-9\-]+$').hasMatch(id)) {
  return const ErrorScreen(message: 'Invalid ID');
}
// Ưu tiên https:// scheme (App Links/Universal Links) thay vì custom scheme
// Yêu cầu re-authentication cho hành động nhạy cảm
```

## SECURE WEBVIEW

```dart
// ❌ NEVER: Load URL tùy ý + JS enabled
WebViewController()
  ..setJavaScriptMode(JavaScriptMode.unrestricted)
  ..loadRequest(Uri.parse(userProvidedUrl));

// ✅ ALWAYS: Domain whitelist + tắt JS nếu không cần
WebViewController()
  ..setJavaScriptMode(JavaScriptMode.disabled)
  ..setNavigationDelegate(NavigationDelegate(
    onNavigationRequest: (request) {
      final uri = Uri.parse(request.url);
      if (uri.scheme != 'https') return NavigationDecision.prevent;
      if (!['example.com', 'cdn.example.com'].any((d) => uri.host.endsWith(d)))
        return NavigationDecision.prevent;
      return NavigationDecision.navigate;
    },
  ));
```

## BIOMETRIC AUTHENTICATION

```dart
// ✅ ALWAYS: Kết hợp biometric với secure storage
import 'package:local_auth/local_auth.dart';

final auth = LocalAuthentication();
final canAuth = await auth.canCheckBiometrics;
if (canAuth) {
  final didAuth = await auth.authenticate(
    localizedReason: 'Xác thực để truy cập',
    options: const AuthenticationOptions(
      biometricOnly: true,
      stickyAuth: true,
    ),
  );
  if (didAuth) {
    final token = await SecureStorageService().getToken();
    // Proceed with token
  }
}
```

## ANTI-DEBUGGING & INTEGRITY

```dart
// ✅ ALWAYS: Phát hiện debug mode trong release
bool get isRelease => const bool.fromEnvironment('dart.vm.product');

void checkIntegrity() {
  assert(() {
    // Chỉ chạy trong debug — tắt trong release
    return true;
  }());
  if (!isRelease) {
    // WARNING: Running in non-release mode
  }
}
```
