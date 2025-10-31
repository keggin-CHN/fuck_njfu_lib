/**
 * 工具函数模块
 */

// Base URL配置
const BASE_URL_PREFIX = "https://webvpn.njfu.edu.cn/webvpn/LjIwMS4xNjkuMjE4LjE2OC4xNjc=";
const LIB_URL_SUFFIX = "/LjIwNS4xNTguMjAwLjE3MS4xNTMuMTUwLjIxNi45Ny4yMTEuMTU2LjE1OC4xNzMuMTQ4LjE1NS4xNTUuMjE3LjEwMC4xNTAuMTY1";
const EDU_URL_SUFFIX = "/LjIxNC4xNTguMTk5LjEwMi4xNjIuMTU5LjIwMi4xNjguMTQ3LjE1MS4xNTYuMTczLjE0OC4xNTMuMTY1";

const DEFAULT_HEADERS = {
  "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36",
  "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
};

/**
 * 获取图书馆URL
 */
export function getLibUrl(path) {
  return `${BASE_URL_PREFIX}${LIB_URL_SUFFIX}/${path}`;
}

/**
 * 获取教育系统URL
 */
export function getEduUrl(path) {
  return `${BASE_URL_PREFIX}${EDU_URL_SUFFIX}/${path}`;
}

/**
 * HTTP请求封装
 */
export async function httpRequest(url, options = {}) {
  const headers = {
    ...DEFAULT_HEADERS,
    ...(options.headers || {})
  };

  const fetchOptions = {
    method: options.method || 'GET',
    headers,
    ...options
  };

  if (options.body) {
    fetchOptions.body = options.body;
  }

  try {
    const response = await fetch(url, fetchOptions);
    return response;
  } catch (error) {
    console.error(`HTTP请求错误 [${options.method || 'GET'}] ${url}:`, error);
    return null;
  }
}

/**
 * AES加密CAS密码
 */
export function encryptCasPassword(password, key) {
  const CHARS = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678";
  
  // 生成随机前缀
  let prefix = "";
  for (let i = 0; i < 64; i++) {
    prefix += CHARS[Math.floor(Math.random() * CHARS.length)];
  }
  
  // 生成随机IV
  let iv = "";
  for (let i = 0; i < 16; i++) {
    iv += CHARS[Math.floor(Math.random() * CHARS.length)];
  }

  const plaintext = prefix + password;
  
  // 使用Web Crypto API进行AES加密
  return encryptAESCBC(plaintext, key, iv);
}

/**
 * 使用Web Crypto API进行AES-CBC加密
 */
async function encryptAESCBC(plaintext, keyString, ivString) {
  const encoder = new TextEncoder();
  const key = await crypto.subtle.importKey(
    'raw',
    encoder.encode(keyString),
    { name: 'AES-CBC', length: 256 },
    false,
    ['encrypt']
  );

  const iv = encoder.encode(ivString);
  const plaintextBytes = encoder.encode(plaintext);

  // PKCS7 padding
  const blockSize = 16;
  const paddingLength = blockSize - (plaintextBytes.length % blockSize);
  const paddedPlaintext = new Uint8Array(plaintextBytes.length + paddingLength);
  paddedPlaintext.set(plaintextBytes);
  for (let i = plaintextBytes.length; i < paddedPlaintext.length; i++) {
    paddedPlaintext[i] = paddingLength;
  }

  const encrypted = await crypto.subtle.encrypt(
    { name: 'AES-CBC', iv },
    key,
    paddedPlaintext
  );

  return btoa(String.fromCharCode(...new Uint8Array(encrypted)));
}

/**
 * RSA加密图书馆密码
 */
export async function encryptLibPassword(plaintextPassword, nonce, publicKeyStr) {
  // 添加PEM头尾
  if (!publicKeyStr.includes("-----BEGIN PUBLIC KEY-----")) {
    publicKeyStr = "-----BEGIN PUBLIC KEY-----\n" + publicKeyStr + "\n-----END PUBLIC KEY-----";
  }

  const message = `${plaintextPassword};${nonce}`;
  
  // 将PEM格式转换为ArrayBuffer
  const pemContents = publicKeyStr.replace(/-----BEGIN PUBLIC KEY-----/, '')
    .replace(/-----END PUBLIC KEY-----/, '')
    .replace(/\n/g, '');
  const binaryDer = atob(pemContents);
  const binaryDerArray = new Uint8Array(binaryDer.length);
  for (let i = 0; i < binaryDer.length; i++) {
    binaryDerArray[i] = binaryDer.charCodeAt(i);
  }

  // 导入公钥
  const publicKey = await crypto.subtle.importKey(
    'spki',
    binaryDerArray.buffer,
    {
      name: 'RSA-OAEP',
      hash: 'SHA-1',
    },
    false,
    ['encrypt']
  );

  // 加密
  const encoder = new TextEncoder();
  const encrypted = await crypto.subtle.encrypt(
    { name: 'RSA-OAEP' },
    publicKey,
    encoder.encode(message)
  );

  return btoa(String.fromCharCode(...new Uint8Array(encrypted)));
}

/**
 * 解析HTML获取表单字段
 */
export function parseFormFields(html) {
  const fields = {};
  
  const patterns = {
    lt: /<input[^>]*name="lt"[^>]*value="([^"]*)"/,
    salt: /<input[^>]*id="pwdDefaultEncryptSalt"[^>]*value="([^"]*)"/,
    dllt: /<input[^>]*name="dllt"[^>]*value="([^"]*)"/,
    execution: /<input[^>]*name="execution"[^>]*value="([^"]*)"/,
    eventId: /<input[^>]*name="_eventId"[^>]*value="([^"]*)"/,
    rmShown: /<input[^>]*name="rmShown"[^>]*value="([^"]*)"/
  };

  for (const [key, pattern] of Object.entries(patterns)) {
    const match = html.match(pattern);
    if (match) {
      fields[key] = match[1];
    }
  }

  return fields;
}

/**
 * 获取日期字符串 (格式: YYYYMMDD)
 */
export function getDateString(daysOffset = 0) {
  const date = new Date();
  date.setDate(date.getDate() + daysOffset);
  
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  
  return `${year}${month}${day}`;
}

/**
 * 将时间戳转换为时间字符串
 */
export function convertTimestampToTime(timestamp) {
  if (!timestamp) return "";
  const date = new Date(timestamp);
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  return `${hours}:${minutes}`;
}

/**
 * 获取座位状态文本
 */
export function getSeatStatusText(resvStatus) {
  const statusMap = {
    1027: "预约中",
    1093: "使用中"
  };
  return statusMap[resvStatus] || "未知";
}

/**
 * CORS响应头
 */
export function corsHeaders() {
  return {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization',
  };
}

/**
 * JSON响应
 */
export function jsonResponse(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      'Content-Type': 'application/json',
      ...corsHeaders()
    }
  });
}

/**
 * 错误响应
 */
export function errorResponse(message, status = 500) {
  return jsonResponse({
    success: false,
    error: message
  }, status);
}
