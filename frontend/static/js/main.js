document.addEventListener('DOMContentLoaded', function () {
    initSeatValidation();
    initPasswordFormValidation();
    initMyReservations();
    initPasswordCollapse();
    initCaptchaHandling();
    initSidebarToggler();
});
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
function initMyReservations() {
    const myReservationsBtn = document.getElementById('myReservationsBtn');
    const collapseReservations = document.getElementById('collapseReservations');
    const reservationsLoading = document.getElementById('reservationsLoading');
    const reservationsContent = document.getElementById('reservationsContent');
    const reservationsError = document.getElementById('reservationsError');
    const noReservations = document.getElementById('noReservations');
    const reservationsList = document.getElementById('reservationsList');
    const errorMessage = document.getElementById('errorMessage');
    if (!myReservationsBtn || !collapseReservations) return;
    const cancelReservationModalElement = document.getElementById('cancelReservationModal');
    const cancelReservationModal = cancelReservationModalElement ?
        new bootstrap.Modal(cancelReservationModalElement) : null;
    const cancelSeatInfo = document.getElementById('cancelSeatInfo');
    const cancelTimeInfo = document.getElementById('cancelTimeInfo');
    const confirmCancelReservation = document.getElementById('confirmCancelReservation');
    let currentUuid = null; 
    collapseReservations.addEventListener('show.bs.collapse', function () {
        const icon = myReservationsBtn.querySelector('.bi-chevron-down');
        if (icon) {
            icon.classList.remove('bi-chevron-down');
            icon.classList.add('bi-chevron-up');
        }
        loadMyReservations();
    });
    collapseReservations.addEventListener('hide.bs.collapse', function () {
        const icon = myReservationsBtn.querySelector('.bi-chevron-up');
        if (icon) {
            icon.classList.remove('bi-chevron-up');
            icon.classList.add('bi-chevron-down');
        }
    });
    function loadMyReservations() {
        reservationsLoading.style.display = 'block';
        reservationsContent.style.display = 'none';
        reservationsError.style.display = 'none';
        fetch('/get_my_reservations')
            .then(response => {
                if (!response.ok) {
                    throw new Error('网络请求失败，状态码: ' + response.status);
                }
                return response.json();
            })
            .then(data => {
                reservationsLoading.style.display = 'none';
                reservationsContent.style.display = 'block';
                if (data.error) {
                    throw new Error(data.error);
                }
                if (!data.reservations || data.reservations.length === 0) {
                    noReservations.style.display = 'block';
                    reservationsList.innerHTML = '';
                } else {
                    noReservations.style.display = 'none';
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
                    data.reservations.forEach(reservation => {
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
                    reservationsList.innerHTML = html;
                    document.querySelectorAll('.cancel-reservation-btn').forEach(btn => {
                        btn.addEventListener('click', function () {
                            currentUuid = this.getAttribute('data-uuid');
                            const seatInfo = this.getAttribute('data-seat');
                            const timeInfo = this.getAttribute('data-time');
                            if (cancelSeatInfo && cancelTimeInfo && cancelReservationModal) {
                                cancelSeatInfo.textContent = seatInfo;
                                cancelTimeInfo.textContent = timeInfo;
                                cancelReservationModal.show();
                            }
                        });
                    });
                }
            })
            .catch(error => {
                reservationsLoading.style.display = 'none';
                reservationsError.style.display = 'block';
                errorMessage.textContent = '加载预约信息失败: ' + error.message;
                console.error('加载预约信息出错:', error);
            });
    }
    if (confirmCancelReservation) {
        confirmCancelReservation.addEventListener('click', function () {
            if (currentUuid) {
                if (cancelReservationModal) {
                    cancelReservationModal.hide();
                }
                reservationsLoading.style.display = 'block';
                reservationsContent.style.display = 'none';
                fetch(`/cancel_reservation/${currentUuid}`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                })
                    .then(response => response.json())
                    .then(data => {
                        if (data.success) {
                            loadMyReservations();
                            alert('预约已成功取消');
                        } else {
                            throw new Error(data.message || '未知错误');
                        }
                    })
                    .catch(error => {
                        reservationsLoading.style.display = 'none';
                        reservationsError.style.display = 'block';
                        errorMessage.textContent = '取消预约失败: ' + error.message;
                        console.error('取消预约出错:', error);
                    });
            }
        });
    }
}
function initPasswordCollapse() {
    const changePasswordBtn = document.getElementById('changePasswordBtn');
    const collapsePassword = document.getElementById('collapsePassword');
    if (!changePasswordBtn || !collapsePassword) return;
    collapsePassword.addEventListener('show.bs.collapse', function () {
        const icon = changePasswordBtn.querySelector('.bi-chevron-down');
        if (icon) {
            icon.classList.remove('bi-chevron-down');
            icon.classList.add('bi-chevron-up');
        }
    });
    collapsePassword.addEventListener('hide.bs.collapse', function () {
        const icon = changePasswordBtn.querySelector('.bi-chevron-up');
        if (icon) {
            icon.classList.remove('bi-chevron-up');
            icon.classList.add('bi-chevron-down');
        }
    });
    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.has('need_captcha')) {
        const bsCollapse = new bootstrap.Collapse(collapsePassword, {
            toggle: true
        });
    }
}
function initCaptchaHandling() {
    const captchaImages = document.querySelectorAll('#captchaImage');
    if (captchaImages.length > 0) {
        captchaImages.forEach(captchaImage => {
            if (!captchaImage.src.includes('/captcha')) {
                refreshCaptcha(captchaImage);
            }
            captchaImage.addEventListener('click', function () {
                refreshCaptcha(this);
            });
        });
    }
    const registerForm = document.getElementById('registerForm');
    if (registerForm) {
        const captchaImage = registerForm.querySelector('#captchaImage');
        if (captchaImage) {
            captchaImage.addEventListener('click', function () {
                refreshCaptcha(this);
            });
        }
    }
    document.querySelectorAll('a[href*="need_captcha"]').forEach(link => {
        link.addEventListener('click', function (e) {
        });
    });
}
function refreshCaptcha(captchaImageElement) {
    if (!captchaImageElement) return;
    const timestamp = new Date().getTime();
    captchaImageElement.src = `/captcha?t=${timestamp}`;
    captchaImageElement.alt = "加载中...";
    captchaImageElement.onload = function () {
        captchaImageElement.alt = "验证码";
    };
    captchaImageElement.onerror = function () {
        captchaImageElement.alt = "加载失败，点击重试";
        console.error("验证码加载失败");
    };
}