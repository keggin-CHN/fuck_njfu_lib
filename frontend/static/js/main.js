// 主要脚本文件
document.addEventListener('DOMContentLoaded', function () {
    // 验证座位号在选择区域后更新最大值
    initSeatValidation();

    // 密码修改表单验证
    initPasswordFormValidation();

    // 已移除立刻签到相关脚本

    // 我的预约功能
    initMyReservations();

    // 修改密码折叠功能
    initPasswordCollapse();

    // 验证码处理
    initCaptchaHandling();

    // 移动端侧边栏切换
    initSidebarToggler();
});

// 初始化侧边栏切换
function initSidebarToggler() {
    const sidebar = document.querySelector('.sidebar');
    const sidebarToggler = document.querySelector('.sidebar-toggler');
    const overlay = document.querySelector('.overlay');

    if (sidebar && sidebarToggler && overlay) {
        sidebarToggler.addEventListener('click', function () {
            sidebar.classList.toggle('is-open');
            overlay.classList.toggle('is-visible');
        });

        overlay.addEventListener('click', function () {
            sidebar.classList.remove('is-open');
            overlay.classList.remove('is-visible');
        });
    }
}

// 初始化座位验证
function initSeatValidation() {
    const areaSelect = document.getElementById('area');
    const seatNumberInput = document.getElementById('seat_number');

    if (areaSelect && seatNumberInput) {
        areaSelect.addEventListener('change', function () {
            const selectedOption = areaSelect.options[areaSelect.selectedIndex];
            const maxSeatsMatch = selectedOption.textContent.match(/\((\d+)个座位\)/);
            if (maxSeatsMatch) {
                const maxSeats = parseInt(maxSeatsMatch[1]);
                seatNumberInput.max = maxSeats;
                if (parseInt(seatNumberInput.value) > maxSeats) {
                    seatNumberInput.value = maxSeats;
                }
            }
        });

        // 初始化时也设置一次
        const selectedOption = areaSelect.options[areaSelect.selectedIndex];
        if (selectedOption) {
            const maxSeatsMatch = selectedOption.textContent.match(/\((\d+)个座位\)/);
            if (maxSeatsMatch) {
                const maxSeats = parseInt(maxSeatsMatch[1]);
                seatNumberInput.max = maxSeats;
            }
        }
    }
}

// 初始化密码表单验证
function initPasswordFormValidation() {
    const passwordForm = document.getElementById('passwordForm');
    if (passwordForm) {
        const passwordTypeSelect = document.getElementById('password_type');
        const oldPasswordInput = document.getElementById('old_password');
        const newPasswordInput = document.getElementById('new_password');
        const confirmPasswordInput = document.getElementById('confirm_password');

        passwordTypeSelect.addEventListener('change', function () {
            if (passwordTypeSelect.value === 'website') {
                oldPasswordInput.required = true;
                oldPasswordInput.parentElement.classList.remove('d-none');
            } else {
                oldPasswordInput.required = false;
                oldPasswordInput.value = '';
            }
        });

        passwordForm.addEventListener('submit', function (e) {
            if (newPasswordInput.value !== confirmPasswordInput.value) {
                e.preventDefault();
                alert('两次输入的新密码不一致！');
                return false;
            }

            if (passwordTypeSelect.value === 'website' && !oldPasswordInput.value.trim()) {
                e.preventDefault();
                alert('修改网站密码时，必须填写当前密码！');
                return false;
            }
        });
    }
}


// 初始化我的预约功能
function initMyReservations() {
    // 获取元素
    const myReservationsBtn = document.getElementById('myReservationsBtn');
    const collapseReservations = document.getElementById('collapseReservations');
    const reservationsLoading = document.getElementById('reservationsLoading');
    const reservationsContent = document.getElementById('reservationsContent');
    const reservationsError = document.getElementById('reservationsError');
    const noReservations = document.getElementById('noReservations');
    const reservationsList = document.getElementById('reservationsList');
    const errorMessage = document.getElementById('errorMessage');

    // 如果不在包含预约的页面上，则退出
    if (!myReservationsBtn || !collapseReservations) return;

    // 取消预约模态框元素
    const cancelReservationModalElement = document.getElementById('cancelReservationModal');
    const cancelReservationModal = cancelReservationModalElement ?
        new bootstrap.Modal(cancelReservationModalElement) : null;
    const cancelSeatInfo = document.getElementById('cancelSeatInfo');
    const cancelTimeInfo = document.getElementById('cancelTimeInfo');
    const confirmCancelReservation = document.getElementById('confirmCancelReservation');

    let currentUuid = null; // 当前要取消的预约UUID

    // 监听折叠面板的展开事件
    collapseReservations.addEventListener('show.bs.collapse', function () {
        // 改变按钮图标方向
        const icon = myReservationsBtn.querySelector('.bi-chevron-down');
        if (icon) {
            icon.classList.remove('bi-chevron-down');
            icon.classList.add('bi-chevron-up');
        }
        loadMyReservations();
    });

    // 监听折叠面板的收起事件
    collapseReservations.addEventListener('hide.bs.collapse', function () {
        // 恢复按钮图标方向
        const icon = myReservationsBtn.querySelector('.bi-chevron-up');
        if (icon) {
            icon.classList.remove('bi-chevron-up');
            icon.classList.add('bi-chevron-down');
        }
    });

    // 加载我的预约信息
    function loadMyReservations() {
        // 显示加载状态
        reservationsLoading.style.display = 'block';
        reservationsContent.style.display = 'none';
        reservationsError.style.display = 'none';

        // 请求预约数据
        fetch('/get_my_reservations')
            .then(response => {
                if (!response.ok) {
                    throw new Error('网络请求失败，状态码: ' + response.status);
                }
                return response.json();
            })
            .then(data => {
                // 隐藏加载状态
                reservationsLoading.style.display = 'none';
                reservationsContent.style.display = 'block';

                // 处理可能的错误消息
                if (data.error) {
                    throw new Error(data.error);
                }

                // 处理数据
                if (!data.reservations || data.reservations.length === 0) {
                    // 没有预约
                    noReservations.style.display = 'block';
                    reservationsList.innerHTML = '';
                } else {
                    // 有预约，显示预约列表
                    noReservations.style.display = 'none';

                    // 构建表格
                    let html = `
                        <div class="table-responsive">
                            <table class="table table-hover table-mobile">
                                <thead>
                                    <tr>
                                        <th>座位</th>
                                        <th>日期</th>
                                        <th>时间</th>
                                        <th>状态</th>
                                        <th>操作</th>
                                    </tr>
                                </thead>
                                <tbody>
                    `;

                    // 添加每条预约记录
                    data.reservations.forEach(reservation => {
                        // 从时间中提取时间部分（去掉日期）
                        const beginTimeParts = reservation.begin_time.split(' ');
                        const endTimeParts = reservation.end_time.split(' ');
                        const timeDisplay = `${beginTimeParts.length > 1 ? beginTimeParts[1] : beginTimeParts[0]} - ${endTimeParts.length > 1 ? endTimeParts[1] : endTimeParts[0]}`;

                        html += `
                            <tr>
                                <td data-title="座位">${reservation.seat_info}</td>
                                <td data-title="日期"><span class="badge ${reservation.day_type === '今日' ? 'bg-info' : 'bg-primary'}">${reservation.day_type}</span></td>
                                <td data-title="时间">${timeDisplay}</td>
                                <td data-title="状态">${reservation.status}</td>
                                <td data-title="操作">
                                    <button class="btn btn-sm btn-danger cancel-reservation-btn"
                                            data-uuid="${reservation.uuid}"
                                            data-seat="${reservation.seat_info}"
                                            data-time="${reservation.day_type} ${timeDisplay}">
                                        取消预约
                                    </button>
                                </td>
                            </tr>
                        `;
                    });

                    html += '</tbody></table></div>';

                    // 更新预约列表
                    reservationsList.innerHTML = html;

                    // 添加取消预约按钮事件
                    document.querySelectorAll('.cancel-reservation-btn').forEach(btn => {
                        btn.addEventListener('click', function () {
                            currentUuid = this.getAttribute('data-uuid');
                            const seatInfo = this.getAttribute('data-seat');
                            const timeInfo = this.getAttribute('data-time');

                            // 设置确认弹窗信息
                            if (cancelSeatInfo && cancelTimeInfo && cancelReservationModal) {
                                cancelSeatInfo.textContent = seatInfo;
                                cancelTimeInfo.textContent = timeInfo;

                                // 显示确认弹窗
                                cancelReservationModal.show();
                            }
                        });
                    });
                }
            })
            .catch(error => {
                // 显示错误信息
                reservationsLoading.style.display = 'none';
                reservationsError.style.display = 'block';
                errorMessage.textContent = '加载预约信息失败: ' + error.message;
                console.error('加载预约信息出错:', error);
            });
    }

    // 确认取消预约
    if (confirmCancelReservation) {
        confirmCancelReservation.addEventListener('click', function () {
            if (currentUuid) {
                // 关闭确认弹窗
                if (cancelReservationModal) {
                    cancelReservationModal.hide();
                }

                // 显示加载状态
                reservationsLoading.style.display = 'block';
                reservationsContent.style.display = 'none';

                // 发送取消请求
                fetch(`/cancel_reservation/${currentUuid}`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                })
                    .then(response => response.json())
                    .then(data => {
                        if (data.success) {
                            // 取消成功，重新加载预约列表
                            loadMyReservations();

                            // 显示成功提示
                            alert('预约已成功取消');
                        } else {
                            // 取消失败
                            throw new Error(data.message || '未知错误');
                        }
                    })
                    .catch(error => {
                        // 显示错误信息
                        reservationsLoading.style.display = 'none';
                        reservationsError.style.display = 'block';
                        errorMessage.textContent = '取消预约失败: ' + error.message;
                        console.error('取消预约出错:', error);
                    });
            }
        });
    }
}

// 初始化修改密码折叠功能
function initPasswordCollapse() {
    const changePasswordBtn = document.getElementById('changePasswordBtn');
    const collapsePassword = document.getElementById('collapsePassword');

    if (!changePasswordBtn || !collapsePassword) return;

    // 监听"修改密码"折叠面板的展开事件
    collapsePassword.addEventListener('show.bs.collapse', function () {
        // 改变按钮图标方向
        const icon = changePasswordBtn.querySelector('.bi-chevron-down');
        if (icon) {
            icon.classList.remove('bi-chevron-down');
            icon.classList.add('bi-chevron-up');
        }
    });

    // 监听"修改密码"折叠面板的收起事件
    collapsePassword.addEventListener('hide.bs.collapse', function () {
        // 恢复按钮图标方向
        const icon = changePasswordBtn.querySelector('.bi-chevron-up');
        if (icon) {
            icon.classList.remove('bi-chevron-up');
            icon.classList.add('bi-chevron-down');
        }
    });

    // 如果URL中包含need_captcha参数，自动展开密码修改面板
    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.has('need_captcha')) {
        // 使用Bootstrap的Collapse API自动展开
        const bsCollapse = new bootstrap.Collapse(collapsePassword, {
            toggle: true
        });
    }
}

// 初始化验证码处理
function initCaptchaHandling() {
    // 获取所有验证码图片元素
    const captchaImages = document.querySelectorAll('#captchaImage');

    // 如果页面上存在验证码图片，则为每个验证码添加刷新功能
    if (captchaImages.length > 0) {
        captchaImages.forEach(captchaImage => {
            // 初始加载验证码（如果尚未加载）
            if (!captchaImage.src.includes('/captcha')) {
                refreshCaptcha(captchaImage);
            }

            // 点击验证码图片刷新
            captchaImage.addEventListener('click', function () {
                refreshCaptcha(this);
            });
        });
    }

    // 初始化注册表单验证码
    const registerForm = document.getElementById('registerForm');
    if (registerForm) {
        const captchaImage = registerForm.querySelector('#captchaImage');
        if (captchaImage) {
            captchaImage.addEventListener('click', function () {
                refreshCaptcha(this);
            });
        }
    }

    // 处理失败历史记录中的验证链接
    document.querySelectorAll('a[href*="need_captcha"]').forEach(link => {
        link.addEventListener('click', function (e) {
            // 正常处理链接，让后端处理需要显示验证码的逻辑
        });
    });
}

// 刷新验证码图片
function refreshCaptcha(captchaImageElement) {
    if (!captchaImageElement) return;

    // 添加随机参数防止缓存
    const timestamp = new Date().getTime();
    captchaImageElement.src = `/captcha?t=${timestamp}`;

    // 添加加载提示
    captchaImageElement.alt = "加载中...";

    // 监听加载完成事件
    captchaImageElement.onload = function () {
        captchaImageElement.alt = "验证码";
    };

    // 监听加载失败事件
    captchaImageElement.onerror = function () {
        captchaImageElement.alt = "加载失败，点击重试";
        console.error("验证码加载失败");
    };
}
