vpn_client((function (){
window.g = {
    ApiUrl: '/ic-web',
    // 管理端所有的下拉框默认显示个数
    OptionsCount: 50,
    // 手机端座位圆点的大小
    mCircleSize: 16,
    // pc端座位圆点的大小
    // 建议选值，8,10,12,16,20,24,28
    pCircleSize: 12,
    proportion: 0.5,  // 0.5:4倍大小; 1:2倍大小
    isPcExpendFirst: true, // pc端首页是否默认展开第一项
    // pc端和手机端首页的导航排序, 展示的顺序和数组中的顺序一致, 如果没有此配置项目, 则默认显示顺序为[1, 2, 4, 8, 16, 32]
    // 代表值, 1:研修间 2:电子阅览室(暂无使用) 4:外借设备(暂无使用) 8:座位 16:活动 32:考研座位
    // sortedNav: [8, 1, 2, 4, 16, 32],
    sortedNav: [1, 8, 32, 16],

    // 手机端预约列表的研修间和座位是否具有分享功能
    isReserveShare: false,

    // 考研座位模块开启请假功能
    isExamCanLeave: false,

    // 研修间，座位，考研座位的预约复制按钮
    isShowCopyReserveBtn: false,
    isShowPass: true, //是否显示修改密码
    // 修改密码的方式: （账号密码登录的情况，cas登录和第三方登录无关）；如果两个都不是，则两种方式都不会使用
    // 第一种：刚进入页面时，账号和密码一致，强制修改密码（1）
    //      判断条件：当前模式是1，isShowPass为true，是一卡通账户，账号密码一样
    // 第二种：首次进入页面，强制修改密码（2）；
    //      判断条件：必须是一卡通账户，首次进入页面（返回的用户信息property不为1），系统配置项changePassword为是，当前模式为2 
    // 第三种：第三方修改密码  刚进入页面时，账号和密码一致，强制修改密码
    //   判断条件：当前模式是3，isShowPass为true，三方修改密码地址不为空，账号密码一样
    editPwdMode: 2, 

    isShowLang: true,  // 是否显示中英文切换
    defaultLang: 1, // 默认语言，1: 中文, 2: 英文 ,3繁体,4,不设置，根据浏览器默认语言

    isShowFeedBack: false, // 是否显示pc端首页的留言反馈, true,显示; false,不显示
    loginMode: 2,  // 1.账号密码登录  2.case认证登录(单点登录)  3. 直接跳转
    
    // 是否关闭 网页端和手机端 的预约暂时离开和提前结束 两功能, 设置为true则关闭; 不设置或者设置为false则不关闭
    // 暂时离开接口
    // isCloseTempleaveAndEndEarly: false,
    // 暂离  false是开启 true关闭
    isCloseTempleave:false,
    // 提前结束  false是开启 true关闭
    isCloseEndEarly:false,
    // 座位子系统，是否隐藏暂时离开功能；需要和 isCloseTempleave 搭配使用，都设置为false时才会显示
    isSeatHideTempleave: false,

    // 设备外借是否显示跨天预约
    isShowBorrowSpanDay: false,

    //座位预约界面是否显示暂离时长，座位图是否增加暂离过滤条件
    isShowSeatLeavingTime:false,

    // 座位筛选总开关，开启并且有值才会显示属性筛选，暂离筛选需要配置 本配置项和isShowSeatLeavingTime
    isShowSeatSelect:false,
    
    // 手机端个人中心是否不显示个人二维码 false显示  true不显示
    isCloseQrCode:false,

    // 是否开始续座功能  true开启  false不开启
    showNextSeat:false,

    // 是否开启快速抢座功能  true开启 false不开启
    useQuickSeat:false,

    // 手机端座位介绍，放到座位区域下边, 禁用介绍按钮  true关闭按钮,false开启按钮
    showSeatIntroduceButton:false,
    
     // 修改密码跳转页面 没有设为""
    editPassUrl:"",

    // 登录记录密码功能开启隐藏  true开启 false隐藏
    showRemberPass:false,

    // 是否开启微信打开手机端绑定功能(需要访问外网) true开启 false关闭
    showBindingWx:false,

    // 单点登录传递跳转地址是否携带pathname(二级域名)
    addressPull:false,

    // 清华 首页左上角展示文字提示 个人中心绑定微信消息二维码修改 true开启
    isTsinghua:false,

    // 手机端是否显示vconsole
    showVConsole:false,

    // 登录框是否展示隐私协议  为空不开启，配置链接地址开启
    showPrivacyAgreement:"",

    // 首页左侧导航栏名字过长是否显示全名 true显示全名 false不显示全名
    showNavName:false,

    // 个人中心 个人预约模块预约状态配置项 2待生效 4已生效 16已违约 128已结束 256待审核 512审核未通过 1024审核通过 2048已暂离 8192待同意 16384举报
    statusOption:[2,4,16,128,2048],

    // 考研座位预约模式  0:座位预约模式;1:研修间跨天预约模式
    examSeatReserveMode:0,

    // 请求是是否不显示referer请求头
    hiddenReferer:true,

    // 手机端个人中心是否显示人脸上传
    showUploadFace:false,

    // 是否判断用户是否是本地账户，开启上传人脸时使用  false,不判断  true判断
    showUserLocal:false,

    // 座位预览、展示页面定时刷新时间  单位s
    seatAreaLoadTime:300,

    // 登录框是否显示密码帮助 true：显示，false：不显示  文件在dist/file/passwordHelp.html
    showPassHelp:false,

    // 座位开启常用座位功能
    useCommonUseSeat:false,

    // 常用座位的相关下拉框配置 数组中第一个数字是默认查询的天数 7为一周 30为一个月 90为三个月;第2个参数是默认排序方式，'RESERVE_DURATION' 为按照时长排序 ，'RESERVE_TIMES' 按照预约方式排序
    pastTime:[7,'RESERVE_DURATION'],

    // 管理端-日常管理-预约管理中默认查询日期，[开始距离今天几天，结束距离今天几天] 例[0,7]今天到7天后 [-7,7]表示7天前到7天后
    reserveDate:[-7,7],

    // 澳门大学接口携带token模式
    isAusToken:false,

    // 选择座位界面如果显示座位名的话, 则判断是否需要以 - 分割, 显示分割后的最后一段
    isSeatNameSplit: false,

    // 空间预约中是否跳到可预约当天
    isJumpToToday: false,

    // 座位图最大放大倍数
    seatZoom:2,

    // 单点登录时如果有多个跳转地址，配置每个地址的配置 电脑端配置
    addressConfigPc:[
        // {
        //     name:"单点登录1",//按钮的名字
        //     typeCode:"4",//typeCode
        // },
        // {
        //     name:"单点登录2",//按钮的名字
        //     typeCode:"5",//typeCode
        // },
        // {
        //     name:"单点登录3",//按钮的名字
        //     typeCode:"6",//typeCode
        // },
    ],
    // 单点登录时如果有多个跳转地址，配置每个地址的配置 手机端配置
    addressConfig:[
        // {
        //     name:"单点登录",//按钮的名字
        //     typeCode:"4",//typeCode
        // },
        // {
        //     name:"单点登录",//按钮的名字
        //     typeCode:"4",//typeCode
        // }
    ],
    // 座位跨天预约是否可选择小时分钟的配置 false不可选小时分钟，true可以选
    reserveSeatTime:true,

    // 登录界面是否显示其他方式登录按钮
    showOtherLoginBtn:false,

    // 手机管理端座位排列不按照用户端 true 按照特殊拖出来的管理端展示 false 按照用户端
    isSeatAreaSort:true,

    // 空间预约提交时是否不显示上传文件按钮  默认false 显示   true 不显示
    isHiddenDownload:false,

    // 预约管理添加违约原因下拉框
    defaultReason:['预约不来','违约','测试'],

    // 主页考研座位菜单根据用户部门匹配，需要本配置项开启，并且后台同时开启相关配置
    onlyShowMyDept: true,

    // 考研座位禁止自由选择时间配置 true开启
    examNotFreeTime: false,

    // 活动预约不显示预约人和主题，系统配置项开启时优先此配置，true开启
    hideReserveUser: true,

    // 参观表单最多人数限制
    maxVisitor: 50
    
}                                
}).toString().slice(13, -2),"");