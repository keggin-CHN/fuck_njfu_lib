# 南京林业大学图书馆系统认证协议逆向记录

本文档记录了南京林业大学图书馆座位预约系统（基于 libseat）在经历系统升级前后，由于前端代码变更带来的两种不同登录鉴权协议（SSO协议与Legacy旧版加密协议）。在目前 `fuck_njfu_lib` 的底层实现中，这两种协议已作为双活回退机制（Dual Fallback）被全量集成。

---

## 1. 协议核心前置条件 (第一级认证)

无论是新版还是旧版协议，系统目前均部署在学校的统一身份认证 WebVPN 代理之下。所有的请求在触达图书馆服务器之前，必须首先进行统一身份（CAS）鉴权。

- **过程**：
  1. 通过统一身份账号和教务系统密码向 CAS 登录接口发起 POST 请求，拿到 WebVPN 下发的 `my_client_ticket` 与代理 Cookie（如 `wengine_vpn_ticket`）。
  2. 获取路由 Cookie，确保后续请求的网络路径能够正确映射。

---

## 2. 新版单点登录 SSO 协议 (当前主链路)

系统在暑假期间进行了重大升级，彻底摒弃了独立密码登录逻辑，全面接入 SSO。只要拥有第一级认证的 WebVPN 票据，就可以直接免密换取图书馆系统的核心身份凭证（Token）。

- **认证流程**：
  1. **申请 SSO CAS 跳转票据**
     向 `ic-web/auth/address`（携带当前页面的重定向地址等信息）发起请求，服务器返回目标为 WebVPN 代理路径的 CAS 票据鉴权 URL (含 Ticket 校验参数)。
  2. **解析 CAS 认证地址**
     提取上述返回的 `cas_url`（如包含 `libseat.njfu.edu.cn`，则通过内部工具自动转换为 `webvpn.njfu.edu.cn` 的内网代理前缀）。
  3. **静默触发 SSO 同步**
     用当前包含 WebVPN 票据的 Session 访问转换后的 `cas_url`。
     此时，服务器并不返回 JSON 数据，而是返回一段包含 JavaScript 赋值跳转的 HTML 页面：
     ```javascript
     window.location.href = 'https://libseat.njfu.edu.cn/ic-web/auth/token?uuid=xxxx&uniToken=xxxx...'
     ```
  4. **正则匹配提取跳转链接**
     由于 Python 和 Android OkHttp 并非真实的浏览器，无法直接执行上述 JavaScript。必须在代码中使用正则表达式拦截匹配 `window.location.href` 中赋的值，并提取出真正的认证落地链接。
  5. **落库注册 Session 并获取 Token**
     携带相同的 Session 请求上一步匹配出的认证落地链接，促使后台在会话中落库用户的 Token。
     随后调用 `ic-web/auth/userInfo` 接口抓取当前账户信息，在返回的 JSON 中可以直接提取出 `accNo`（账号ID）和 `token`（图书馆预约凭证），认证完成。

---

## 3. 旧版高强度加密协议 (Legacy Fallback 链路)

这是图书馆系统升级前的独立认证方式。它不依赖 SSO，而是强制要求用户输入图书馆设置的独立“服务密码”（如 `njfu23001x!`）。目前服务端该接口已被隐藏，但为防日后回滚，依然保留为系统的回退备用机制。

- **认证流程**：
  1. **获取加密盐 (Nonce) 和公钥 (Public Key)**
     发送请求至 `ic-web/login/publicKey` 接口，服务端会返回一组临时的 `nonceStr`（如 16 位字母数字组合）以及一段 RSA 公钥。
  2. **密码高强度混淆加密**
     将用户输入的纯文本图书馆密码先在本地环境经过前端的 AES 混合编码逻辑（或结合 RSA 公钥通过特定 PADDING 方式加密），生成极长的一段乱码字符。
     *在我们的 Python 逆向模块中体现为 `encrypt_lib_password(password, nonce, publicKey)` 函数，而在 Android 端表现为 `RSACipher.encrypt`*。
  3. **提交密文请求获取 Token**
     构造请求 Payload：
     ```json
     {
       "logonName": "用户学号",
       "password": "高强度加密后的密码文本",
       "captcha": "",
       "consoleType": 16,
       "privacy": true
     }
     ```
     发送 POST 请求至 `ic-web/login/user`。如果密码加密逻辑完全符合后端的解密要求，服务端即返回包含 `accNo` 和 `token` 的 JSON 用户数据包。
