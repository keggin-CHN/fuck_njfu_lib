# NJFU CAS 协议逆向分析报告

**日期**: 2026-06-29  
**作者**: keggin  
**起因**: fuck_njfu_lib 项目突然无法登录，推测 CAS 统一认证协议发生变化  

---

## 1. 背景

NJFU（南京林业大学）图书馆座位预约系统通过 **WebVPN + CAS 统一认证 + 图书馆 API** 三层架构实现。原有项目 `fuck_njfu_lib` 使用 Python 后端 + Android App 两套客户端，通过逆向 WebVPN 和 CAS 登录流程实现自动预约。

2026 年 6 月底，项目突然无法登录，表现为 CAS 第一级认证失败，无法获取 `my_client_ticket`。

---

## 2. 逆向过程

### 2.1 发现问题

运行测试脚本时，CAS 登录页面返回 200，但旧代码期望的字段全部缺失：

| 旧字段 | 新页面 |
|--------|--------|
| `lt` (hidden input) | ❌ 不存在 |
| `pwdDefaultEncryptSalt` (hidden input) | ❌ 不存在 |
| `dllt` (hidden input) | ❌ 不存在 |
| `rmShown` (hidden input) | ❌ 不存在 |
| `execution` | ✅ 保留 |
| `_eventId` | ✅ 保留 |

新增字段：
- `encrypted` (hidden, value=`true`)
- `loginType` (hidden, value=`1`)

### 2.2 分析 JS 加密逻辑

登录页引用的关键 JS 文件：

```
authserver/js/cas.js           → 页面初始化，无加密逻辑
authserver/js/security.js      → RSA 加密库 (RSAUtils)
authserver/themes/sudy_njfu/js/login.js  → 登录表单提交逻辑 (90KB)
```

**关键发现**: `security.js` 通过相对路径 `js/security.js` 引用时返回 HTTP 500，但通过 WebVPN 完整路径可正常访问（18KB RSA 库）。

### 2.3 提取新加密算法

从 `login.js` 第 2047-2051 行提取到关键加密代码：

```javascript
if (document.getElementById("encrypted")) {
    if (password.length != 256) {
        RSAUtils.setMaxDigits(131);
        var key = RSAUtils.getKeyPair(
            "010001",  // exponent (65537)
            '',
            "008aed7e057fe8f14c73550b0e6467b023616ddc8fa91846d2613cdb7f7621e3" +
            "cada4cd5d812d627af6b87727ade4e26d26208b7326815941492b2204c3167ab" +
            "2d53df1e3a2c9153bdb7c8c2e968df97a5e7e01cc410f92c4c2c2fba529b3ee9" +
            "88ebc1fca99ff5119e036d732c368acf8beba01aa2fdafa45b21e4de4928d0d403"
        );
        var result = RSAUtils.encryptedString(key, password);
        $("#password").val(result);
    }
}
```

---

## 3. 新旧协议对比

### 3.1 CAS 第一级认证（变化）

| 维度 | 旧版 | 新版 (2025+) |
|------|------|-------------|
| **加密算法** | AES-128-CBC | RSA-1024 (textbook) |
| **密钥来源** | 每次从登录页动态获取 `pwdDefaultEncryptSalt` | 硬编码在 `login.js` 中 |
| **加密模式** | `AES/CBC/PKCS5Padding` | Textbook RSA（无 PKCS#1 padding） |
| **IV** | 随机 16 字符 | 无 |
| **明文格式** | `64字节随机前缀 + 密码` | `密码`（charCode 直接打包） |
| **输出格式** | Base64 | 256 字符 hex |
| **表单字段** | `lt`, `dllt`, `rmShown`, `execution`, `_eventId` | `execution`, `encrypted=true`, `loginType`, `_eventId` |
| **needCaptcha** | 需要 `pwdEncrypt2` 参数 | 只需 `username` |

### 3.2 图书馆第二级认证（未变）

仍然使用：
- API 获取动态 RSA 公钥 + nonce
- PKCS#1 v1.5 加密 `{password};{nonce}`
- Base64 编码输出

### 3.3 route cookie（部分变化）

旧版从响应体提取 `route=xxx`，新版返回 `"faild"`，但不影响认证流程。

---

## 4. Textbook RSA 加密详解

### 4.1 算法描述

NJFU CAS 使用的 RSA 加密方式不同于标准 PKCS#1，是 **textbook RSA**（裸 RSA，无填充）：

```
密文 C = 明文 M ^ E mod N
```

其中：
- `N` (modulus) = `0x008aed7e057fe8f14c73550b0e6467b023616ddc8fa91846d2613cdb7f7621e3cada4cd5d812d627af6b87727ade4e26d26208b7326815941492b2204c3167ab2d53df1e3a2c9153bdb7c8c2e968df97a5e7e01cc410f92c4c2c2fba529b3ee988ebc1fca99ff5119e036d732c368acf8beba01aa2fdafa45b21e4de4928d0d403` (1024-bit)
- `E` (exponent) = `0x010001` (65537)

### 4.2 明文打包方式

前端 `RSAUtils.encryptedString` 的打包逻辑（与常见 RSA 库不同）：

```javascript
// 1. 密码字符串转 charCode 数组
var a = [];
for (var i = 0; i < password.length; i++) {
    a[i] = password.charCodeAt(i);  // ASCII: 0-127
}

// 2. 填充到 chunkSize (126) 的倍数
while (a.length % 126) { a.push(0); }

// 3. 每 2 个 charCode 打包成 1 个 16-bit digit (little-endian)
for (k = i; k < i + 126; k += 2) {
    block.digits[j] = a[k] + (a[k+1] << 8);
}

// 4. BigInt 计算: m = sum(digit[i] * 65536^i)
// 5. RSA: c = m^e mod n
// 6. 输出: hex(c)，填充到 256 字符
```

### 4.3 Python 实现

```python
CAS_RSA_MODULUS_HEX = "008aed7e057fe8f14c73550b0e6467b0..."
CAS_RSA_EXPONENT_HEX = "010001"

def encrypt_cas_password(password):
    n = int(CAS_RSA_MODULUS_HEX, 16)
    e = int(CAS_RSA_EXPONENT_HEX, 16)
    chunk_size = 126  # 2 * biHighIndex(modulus) = 2 * 63

    char_codes = [ord(c) for c in password]
    while len(char_codes) % chunk_size:
        char_codes.append(0)

    m = 0
    for i in range(0, chunk_size, 2):
        digit = char_codes[i] + char_codes[i + 1] * 256
        m += digit * (65536 ** (i // 2))

    c = pow(m, e, n)
    return format(c, '0256x')  # 256 字符 hex
```

### 4.4 Java 实现 (Android)

```java
public static String encrypt(String password) {
    BigInteger n = new BigInteger(MODULUS_HEX, 16);
    BigInteger e = new BigInteger(EXPONENT_HEX, 16);

    int[] charCodes = new int[password.length()];
    for (int i = 0; i < password.length(); i++)
        charCodes[i] = password.charAt(i);

    int paddedLength = charCodes.length;
    while (paddedLength % 126) paddedLength++;
    int[] padded = new int[paddedLength];
    System.arraycopy(charCodes, 0, padded, 0, charCodes.length);

    BigInteger m = BigInteger.ZERO;
    for (int i = 0; i < 126; i += 2) {
        int digit = padded[i] + (padded[i + 1] << 8);
        m = m.add(BigInteger.valueOf(digit)
            .multiply(BigInteger.valueOf(65536).pow(i / 2)));
    }

    BigInteger c = m.modPow(e, n);
    String hex = c.toString(16);
    while (hex.length() < 256) hex = "0" + hex;
    return hex;
}
```

---

## 5. 修改的文件

### 5.1 Backend (Python)

| 文件 | 修改内容 |
|------|---------|
| `backend/utils/auth_manager.py` | `encrypt_cas_password`: AES-CBC → textbook RSA |
| | `first_level_auth`: 移除 lt/dllt/rmShown/salt，新增 encrypted/loginType |
| | `check_need_captcha`: 移除 pwdEncrypt2 参数 |
| | `authenticate_with_captcha`: 同步更新表单字段 |
| | 移除 `from Crypto.Cipher import AES` 和 `from Crypto.Util.Padding import pad` |

### 5.2 Android (Java)

| 文件 | 修改内容 |
|------|---------|
| `android/.../crypto/CASRSACipher.java` | **新建** — textbook RSA 加密实现 |
| `android/.../auth/CASAuthenticator.java` | AES → CASRSACipher，移除旧字段 |
| `android/.../network/ApiConstants.java` | `getNeedCaptchaUrl` 去掉 salt 参数 |

### 5.3 Server_api

无需修改 — 直接复用已修好的 `backend/utils/auth_manager.py`。

---

## 6. 测试验证

### 6.1 认证测试（通过 KURUN VPN 服务器中转）

```
✅ CAS 登录: 302 → ticket → my_client_ticket
✅ 图书馆登录: token + accNo 获取成功
✅ 用户信息: 周文杰 / 林产化学与材料创新高地 / accNo=143934679
```

### 6.2 预约查询验证

```
📅 今天 (2026-06-29): 四层A区 4F-A427, 01:00~14:00, 已签到
📅 明天 (2026-06-30): 四层A区 4F-A428, 02:00~14:00, 已预约
```

### 6.3 Git 提交

```
7fab5b5 fix: 适配 NJFU CAS 新版 RSA 加密协议          (backend)
6fc8aa1 fix(android): 适配 CAS 新版 RSA 加密协议       (android)
```

---

## 7. 注意事项

1. **硬编码公钥风险**: CAS RSA 公钥硬编码在 JS 和客户端代码中，学校更换密钥对时需要同步更新。
2. **Textbook RSA 安全性**: 无填充的 RSA 存在已知安全问题（如 chosen-ciphertext 攻击），但这是学校 CAS 的设计，我们无法修改。
3. **WebVPN URL 路径**: WebVPN 的 URL 编码路径（`LjIwMS4xNjku...`）是 base64 编码的内网地址，可能随学校网络调整变化。
4. **从 AWS 测试需要代理**: NJFU WebVPN 对海外 IP 不可达，测试需通过国内或可达的 VPN 服务器中转。

---

## 8. 附录：关键 URL

| 用途 | URL |
|------|-----|
| WebVPN 首页 | `https://webvpn.njfu.edu.cn/` |
| CAS 登录页 | `https://webvpn.njfu.edu.cn/webvpn/.../authserver/login?service=...` |
| CAS needCaptcha | `https://webvpn.njfu.edu.cn/webvpn/.../authserver/needCaptcha.html` |
| CAS security.js (RSA 库) | `https://webvpn.njfu.edu.cn/webvpn/.../authserver/js/security.js` |
| CAS login.js (加密逻辑) | `https://webvpn.njfu.edu.cn/webvpn/.../authserver/themes/sudy_njfu/js/login.js` |
| 图书馆公钥 | `https://webvpn.njfu.edu.cn/webvpn/.../ic-web/login/publicKey` |
| 图书馆登录 | `https://webvpn.njfu.edu.cn/webvpn/.../ic-web/login/user` |
| 预约查询 | `https://webvpn.njfu.edu.cn/webvpn/.../ic-web/reserve/resvInfo` |
