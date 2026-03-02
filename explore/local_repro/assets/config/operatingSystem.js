vpn_client((function (){
;(function () {
    const config = {
        
        localAddress: "http://localhost:8080/mobile.html#/login"
    };
    
    const os = (function () {
        const ua = navigator.userAgent,
            isWindowsPhone = /(?:Windows Phone)/.test(ua),
            isSymbian = /(?:SymbianOS)/.test(ua) || isWindowsPhone,
            isAndroid = /(?:Android)/.test(ua),
            isFireFox = /(?:Firefox)/.test(ua),
            isChrome = /(?:Chrome|CriOS)/.test(ua),
            isTablet = /(?:iPad|PlayBook)/.test(ua) || (isAndroid && !/(?:Mobile)/.test(ua)) || (isFireFox && /(?:Tablet)/.test(ua)),
            isPhone = /(?:iPhone)/.test(ua) && !isTablet,
            isPc = !isPhone && !isAndroid && !isSymbian;
        return {
            isTablet: isTablet,
            isPhone: isPhone,
            isAndroid: isAndroid,
            isPc: isPc
        };
    })();
    if (os.isAndroid || os.isPhone) {
        var pathname = window.location.pathname
        if(/scancode\.html/.test(pathname)){
            // 扫码登录
            window.location.href = `${window.location.protocol+"//"+window.location.host}${path}/scancode.html${window.location.hash}`;
        } else if(/actcode\.html/.test(pathname)) {
            // 扫描活动二维码
            window.location.href = `${window.location.protocol+"//"+window.location.host}${path}/actcode.html${window.location.hadh}`;
        } else {
            var arr = pathname.split('/')
            arr.splice(arr.length - 1, 1)
            var path = arr.join('/');
            // console.log('sss: ', `${window.location.protocol+"//"+window.location.host}${path}/mobile.html#/`);
            // alert('要跳转到手机端页面了');
            // alert(`${window.location.protocol+"//"+window.location.host}${path}/mobile.html#/login`);
            // window.document.write(`${window.location.protocol+"//"+window.location.host}${path}/mobile.html#/login`);
            let hash = location.hash;  // 获取当前页面 URL 的哈希部分
            if(hash){
                let hashpath = hash.substring(2);
                window.location.href = `${window.location.protocol+"//"+window.location.host}${path}/mobile.html#/` + hashpath + location.search;
            }else{
                window.location.href = `${window.location.protocol+"//"+window.location.host}${path}/mobile.html#/` + location.search;
            }
            // window.event.returnValue = false;
            // window.location.href = `${window.location.protocol+"//"+window.location.host}${path}/scancode.html`;
            //移动端
            // window.location.href = config.localAddress;
        }
    }
})()
}).toString().slice(13, -2),"");