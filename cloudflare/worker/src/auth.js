/**
 * 认证模块
 */

import { getLibUrl, getEduUrl, httpRequest, encryptCasPassword, encryptLibPassword, parseFormFields } from './utils.js';

/**
 * 图书馆认证器类
 */
export class LibraryAuthenticator {
  constructor(username, eduPassword, libPassword) {
    this.username = username;
    this.eduPassword = eduPassword;
    this.libPassword = libPassword;
    this.myClientTicket = null;
    this.token = null;
    this.accNo = null;
    this.lastAuthTime = null;
  }

  /**
   * 获取初始client ticket
   */
  async getInitialClientTicket() {
    const url = "https://webvpn.njfu.edu.cn/rump_frontend/login/";
    const response = await httpRequest(url);
    
    if (response && response.ok) {
      const setCookie = response.headers.get('set-cookie');
      if (setCookie) {
        const match = setCookie.match(/my_client_ticket=([^;]+)/);
        if (match) {
          return match[1];
        }
      }
    }
    return null;
  }

  /**
   * 第一级认证 (CAS统一认证)
   */
  async firstLevelAuth() {
    const myClientTicket = await this.getInitialClientTicket();
    if (!myClientTicket) {
      return { success: false, ticket: null, message: '无法获取初始ticket' };
    }

    const loginPrepareUrl = getEduUrl(
      "authserver/login?service=https%3A%2F%2Fwebvpn.njfu.edu.cn%2Frump_frontend%2FloginFromCas%2F"
    );

    const prepareResponse = await httpRequest(loginPrepareUrl, {
      headers: {
        'Cookie': `my_client_ticket=${myClientTicket}`
      }
    });

    if (!prepareResponse || !prepareResponse.ok) {
      return { success: false, ticket: null, message: '准备登录失败' };
    }

    const html = await prepareResponse.text();
    const fields = parseFormFields(html);

    if (!fields.lt || !fields.salt || !fields.dllt || !fields.execution || !fields.eventId || !fields.rmShown) {
      return { success: false, ticket: null, message: '解析登录表单失败' };
    }

    // 加密密码
    const encryptedPassword = await encryptCasPassword(this.eduPassword, fields.salt);

    // 构建登录数据
    const loginUrl = getEduUrl(
      "authserver/login?vpn-0&service=https%3A%2F%2Fwebvpn.njfu.edu.cn%2Frump_frontend%2FloginFromCas%2F"
    );

    const formData = new URLSearchParams({
      "vpn-0": "",
      "service": "https://webvpn.njfu.edu.cn/rump_frontend/loginFromCas/",
      "username": this.username,
      "password": encryptedPassword,
      "lt": fields.lt,
      "dllt": fields.dllt,
      "execution": fields.execution,
      "_eventId": fields.eventId,
      "rmShown": fields.rmShown
    });

    const loginResponse = await httpRequest(loginUrl, {
      method: 'POST',
      headers: {
        'Cookie': `my_client_ticket=${myClientTicket}`,
        'Content-Type': 'application/x-www-form-urlencoded'
      },
      body: formData.toString(),
      redirect: 'manual'
    });

    if (!loginResponse || loginResponse.status !== 302) {
      return { success: false, ticket: null, message: '统一认证密码错误' };
    }

    const location = loginResponse.headers.get('Location');
    if (!location) {
      return { success: false, ticket: null, message: '未获取到重定向地址' };
    }

    const ticketMatch = location.match(/ticket=([^&]+)/);
    if (!ticketMatch) {
      return { success: false, ticket: null, message: '未获取到ticket' };
    }

    const ticket = ticketMatch[1];
    const finalAuthUrl = `https://webvpn.njfu.edu.cn/rump_frontend/loginFromCas/?ticket=${ticket}`;
    const finalResponse = await httpRequest(finalAuthUrl, {
      headers: {
        'Cookie': `my_client_ticket=${myClientTicket}`
      }
    });

    if (!finalResponse || !finalResponse.ok) {
      return { success: false, ticket: null, message: '最终认证失败' };
    }

    // 获取更新后的ticket
    const finalSetCookie = finalResponse.headers.get('set-cookie');
    let finalTicket = myClientTicket;
    if (finalSetCookie) {
      const match = finalSetCookie.match(/my_client_ticket=([^;]+)/);
      if (match) {
        finalTicket = match[1];
      }
    }

    this.myClientTicket = finalTicket;
    return { success: true, ticket: finalTicket, message: '第一级认证成功' };
  }

  /**
   * 获取公钥
   */
  async getPublicKey() {
    const publicKeyUrl = getLibUrl("ic-web/login/publicKey?vpn-12-libseat.njfu.edu.cn");
    const response = await httpRequest(publicKeyUrl, {
      headers: {
        'Cookie': `my_client_ticket=${this.myClientTicket}`,
        'Accept': 'application/json, text/plain, */*'
      }
    });

    if (response && response.ok) {
      try {
        const data = await response.json();
        if (data.code === 0) {
          return {
            publicKey: data.data.publicKey,
            nonce: data.data.nonceStr
          };
        }
      } catch (error) {
        console.error('解析公钥响应失败:', error);
      }
    }
    return { publicKey: null, nonce: null };
  }

  /**
   * 第二级认证 (图书馆系统)
   */
  async secondLevelAuth(maxAttempts = 3) {
    if (!this.myClientTicket) {
      return { success: false, token: null, accNo: null, message: '缺少client ticket' };
    }

    const loginUrl = getLibUrl("ic-web/login/user?vpn-12-libseat.njfu.edu.cn");

    for (let attempt = 1; attempt <= maxAttempts; attempt++) {
      console.log(`第二级认证: 尝试第 ${attempt} 次`);

      try {
        const { publicKey, nonce } = await this.getPublicKey();
        if (!publicKey || !nonce) {
          await new Promise(resolve => setTimeout(resolve, 1000));
          continue;
        }

        let encryptedPassword;
        try {
          encryptedPassword = await encryptLibPassword(this.libPassword, nonce, publicKey);
        } catch (error) {
          console.error('加密图书馆密码失败:', error);
          await new Promise(resolve => setTimeout(resolve, 1000));
          continue;
        }

        const payload = {
          logonName: this.username,
          password: encryptedPassword,
          captcha: "",
          consoleType: 16,
          privacy: true
        };

        const response = await httpRequest(loginUrl, {
          method: 'POST',
          headers: {
            'Cookie': `my_client_ticket=${this.myClientTicket}`,
            'Accept': 'application/json, text/plain, */*',
            'Content-Type': 'application/json;charset=UTF-8'
          },
          body: JSON.stringify(payload)
        });

        if (response && response.ok) {
          try {
            const result = await response.json();
            if (result.code === 0) {
              this.token = result.data.token;
              this.accNo = result.data.accNo;
              this.lastAuthTime = Date.now();
              return { 
                success: true, 
                token: this.token, 
                accNo: this.accNo, 
                message: '第二级认证成功' 
              };
            }
          } catch (error) {
            console.error('解析登录响应失败:', error);
          }
        }

        await new Promise(resolve => setTimeout(resolve, 1000));
      } catch (error) {
        console.error(`第二级认证尝试 ${attempt} 失败:`, error);
        await new Promise(resolve => setTimeout(resolve, 1000));
      }
    }

    return { success: false, token: null, accNo: null, message: '图书馆密码错误或认证失败' };
  }

  /**
   * 完整认证流程
   */
  async authenticate() {
    try {
      // 第一级认证
      const firstAuth = await this.firstLevelAuth();
      if (!firstAuth.success) {
        return { 
          success: false, 
          message: firstAuth.message 
        };
      }

      // 第二级认证
      const secondAuth = await this.secondLevelAuth();
      if (!secondAuth.success) {
        return { 
          success: false, 
          message: secondAuth.message 
        };
      }

      return { 
        success: true, 
        message: '认证成功',
        token: this.token,
        accNo: this.accNo,
        ticket: this.myClientTicket
      };
    } catch (error) {
      return { 
        success: false, 
        message: `认证过程中发生错误: ${error.message}` 
      };
    }
  }

  /**
   * 验证认证是否有效
   */
  async isValid() {
    if (!this.myClientTicket || !this.token || !this.accNo) {
      return false;
    }

    try {
      const today = getDateString(0);
      const url = getLibUrl("ic-web/reserve/resvInfo");
      const params = new URLSearchParams({
        "vpn-12-libseat.njfu.edu.cn": "",
        "needStatus": "8454",
        "unneedStatus": "128",
        "beginDate": today,
        "endDate": today
      });

      const response = await httpRequest(`${url}?${params.toString()}`, {
        headers: {
          'Cookie': `my_client_ticket=${this.myClientTicket}`,
          'token': this.token,
          'lan': '1',
          'Accept': 'application/json, text/plain, */*'
        }
      });

      if (response && response.ok) {
        const result = await response.json();
        return result.code === 0;
      }
      return false;
    } catch (error) {
      console.error('验证认证有效性时出错:', error);
      return false;
    }
  }
}

function getDateString(daysOffset) {
  const date = new Date();
  date.setDate(date.getDate() + daysOffset);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}${month}${day}`;
}
