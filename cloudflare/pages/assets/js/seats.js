/**
 * 座位查询页面脚本
 */

let currentDaysOffset = 0;
let allAreasData = null;
let currentFloor = 'all';

// 页面加载时获取数据
document.addEventListener('DOMContentLoaded', function() {
    loadSeatsData(0);
});

/**
 * 加载座位数据
 */
async function loadSeatsData(daysOffset) {
    currentDaysOffset = daysOffset;

    const loadingIndicator = document.getElementById('loadingIndicator');
    const seatsDisplay = document.getElementById('seatsDisplay');
    const errorDisplay = document.getElementById('errorDisplay');
    const todayBtn = document.getElementById('todayBtn');
    const tomorrowBtn = document.getElementById('tomorrowBtn');

    // 更新按钮状态
    if (daysOffset === 0) {
        todayBtn.classList.add('active', 'btn-primary');
        todayBtn.classList.remove('btn-outline-primary');
        tomorrowBtn.classList.remove('active', 'btn-primary');
        tomorrowBtn.classList.add('btn-outline-primary');
    } else {
        tomorrowBtn.classList.add('active', 'btn-primary');
        tomorrowBtn.classList.remove('btn-outline-primary');
        todayBtn.classList.remove('active', 'btn-primary');
        todayBtn.classList.add('btn-outline-primary');
    }

    // 显示加载状态
    loadingIndicator.style.display = 'block';
    seatsDisplay.style.display = 'none';
    errorDisplay.style.display = 'none';

    try {
        const response = await fetch(`${window.CONFIG.API_BASE_URL}/api/seats/summary?days_offset=${daysOffset}`);
        const data = await response.json();

        if (data.success) {
            allAreasData = data;
            displayTotalStats(data.total);
            displayFloorFilter(data.floors);
            displayAreas(data.areas, data.floors);
            loadingIndicator.style.display = 'none';
            seatsDisplay.style.display = 'block';
        } else {
            showError(data.message || '获取座位数据失败');
            loadingIndicator.style.display = 'none';
        }
    } catch (error) {
        console.error('Error:', error);
        showError('网络请求失败，请检查配置或稍后重试');
        loadingIndicator.style.display = 'none';
    }
}

/**
 * 显示总览统计
 */
function displayTotalStats(total) {
    document.getElementById('totalSeats').textContent = total.total || 0;
    document.getElementById('availableSeats').textContent = total.available || 0;
    document.getElementById('occupiedSeats').textContent = total.occupied || 0;
    document.getElementById('occupiedRate').textContent = (total.rate || 0) + '%';
}

/**
 * 显示楼层筛选按钮
 */
function displayFloorFilter(floors) {
    const filterContainer = document.getElementById('floorFilter');
    
    // 清空现有按钮
    filterContainer.innerHTML = `
        <button type="button" class="btn btn-outline-secondary active" onclick="filterByFloor('all')">
            全部楼层
        </button>
    `;

    // 添加楼层按钮
    const floorNumbers = Object.keys(floors).sort((a, b) => parseInt(a) - parseInt(b));
    floorNumbers.forEach(floor => {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'btn btn-outline-secondary';
        btn.textContent = floor + '层';
        btn.onclick = () => filterByFloor(floor);
        filterContainer.appendChild(btn);
    });
}

/**
 * 按楼层筛选
 */
function filterByFloor(floor) {
    currentFloor = floor;

    // 更新按钮状态
    const buttons = document.querySelectorAll('#floorFilter .btn');
    buttons.forEach(btn => {
        if ((floor === 'all' && btn.textContent === '全部楼层') ||
            (floor !== 'all' && btn.textContent === floor + '层')) {
            btn.classList.add('active');
            btn.classList.remove('btn-outline-secondary');
            btn.classList.add('btn-secondary');
        } else {
            btn.classList.remove('active');
            btn.classList.add('btn-outline-secondary');
            btn.classList.remove('btn-secondary');
        }
    });

    // 重新显示区域
    displayAreas(allAreasData.areas, allAreasData.floors);
}

/**
 * 显示区域信息
 */
function displayAreas(areas, floors) {
    const container = document.getElementById('areasContainer');
    container.innerHTML = '';

    // 按楼层分组
    const floorGroups = {};
    for (let areaName in areas) {
        const area = areas[areaName];
        const floor = area.floor;

        // 应用楼层筛选
        if (currentFloor !== 'all' && floor !== parseInt(currentFloor)) {
            continue;
        }

        if (!floorGroups[floor]) {
            floorGroups[floor] = [];
        }
        floorGroups[floor].push({
            name: areaName,
            ...area
        });
    }

    // 按楼层排序并显示
    const sortedFloors = Object.keys(floorGroups).sort((a, b) => parseInt(b) - parseInt(a));
    sortedFloors.forEach(floor => {
        // 楼层标题
        const floorTitle = document.createElement('div');
        floorTitle.className = 'col-12 mt-4 mb-2';
        floorTitle.innerHTML = `<h4><i class="bi bi-layers"></i> ${floor}层</h4><hr>`;
        container.appendChild(floorTitle);

        // 该楼层的所有区域
        floorGroups[floor].forEach(area => {
            const col = document.createElement('div');
            col.className = 'col-md-6 col-lg-4 mb-3';

            const availablePercent = area.stats.total > 0
                ? Math.round((area.stats.available / area.stats.total) * 100)
                : 0;

            const statusClass = area.stats.available > 0 ? 'success' : 'danger';
            const statusIcon = area.stats.available > 0 ? 'check-circle' : 'x-circle';

            col.innerHTML = `
                <div class="card h-100 area-card">
                    <div class="card-header bg-${statusClass} text-white">
                        <h6 class="mb-0">
                            <i class="bi bi-${statusIcon} me-2"></i>${area.name}
                        </h6>
                    </div>
                    <div class="card-body">
                        <div class="row text-center">
                            <div class="col-4">
                                <div class="mb-1 text-muted small">总座位</div>
                                <div class="h5 mb-0">${area.stats.total}</div>
                            </div>
                            <div class="col-4">
                                <div class="mb-1 text-muted small">空闲</div>
                                <div class="h5 mb-0 text-success">${area.stats.available}</div>
                            </div>
                            <div class="col-4">
                                <div class="mb-1 text-muted small">占用</div>
                                <div class="h5 mb-0 text-danger">${area.stats.occupied}</div>
                            </div>
                        </div>
                        <div class="progress mt-3" style="height: 25px;">
                            <div class="progress-bar bg-success" role="progressbar"
                                 style="width: ${availablePercent}%"
                                 aria-valuenow="${availablePercent}" aria-valuemin="0" aria-valuemax="100">
                                ${availablePercent}% 空闲
                            </div>
                        </div>
                    </div>
                    <div class="card-footer">
                        <button class="btn btn-sm btn-outline-primary w-100" onclick="viewAreaDetail('${area.name}')">
                            <i class="bi bi-list-ul me-1"></i>查看详情
                        </button>
                    </div>
                </div>
            `;
            container.appendChild(col);
        });
    });
}

/**
 * 查看区域详情
 */
async function viewAreaDetail(areaName) {
    const modal = new bootstrap.Modal(document.getElementById('seatDetailModal'));
    const modalTitle = document.getElementById('modalTitle');
    const modalBody = document.getElementById('modalBody');

    modalTitle.textContent = areaName + ' - 座位详情';
    modalBody.innerHTML = `
        <div class="text-center py-3">
            <div class="spinner-border" role="status">
                <span class="visually-hidden">加载中...</span>
            </div>
        </div>
    `;

    modal.show();

    try {
        const response = await fetch(
            `${window.CONFIG.API_BASE_URL}/api/seats/detail?area=${encodeURIComponent(areaName)}&days_offset=${currentDaysOffset}`
        );
        const data = await response.json();

        if (data.success) {
            displaySeatDetails(data.seats);
        } else {
            modalBody.innerHTML = `
                <div class="alert alert-danger">获取数据失败: ${data.message}</div>
            `;
        }
    } catch (error) {
        console.error('Error:', error);
        modalBody.innerHTML = `
            <div class="alert alert-danger">获取数据失败，请稍后重试</div>
        `;
    }
}

/**
 * 显示座位详情
 */
function displaySeatDetails(seats) {
    const modalBody = document.getElementById('modalBody');

    if (!seats || seats.length === 0) {
        modalBody.innerHTML = '<div class="alert alert-info">暂无座位数据</div>';
        return;
    }

    let html = '<div class="table-responsive"><table class="table table-hover">';
    html += '<thead><tr><th>座位号</th><th>状态</th><th>预约信息</th></tr></thead><tbody>';

    seats.forEach(seat => {
        const statusBadge = seat.isAvailable
            ? '<span class="badge bg-success">空闲</span>'
            : '<span class="badge bg-danger">已占用</span>';

        let resvInfo = '';
        if (seat.reservations && seat.reservations.length > 0) {
            resvInfo = seat.reservations.map(resv =>
                `<div class="small">${resv.startTime} - ${resv.endTime} (${resv.status})</div>`
            ).join('');
        } else {
            resvInfo = '<span class="text-muted small">-</span>';
        }

        html += `
            <tr>
                <td><strong>${seat.devName}</strong></td>
                <td>${statusBadge}</td>
                <td>${resvInfo}</td>
            </tr>
        `;
    });

    html += '</tbody></table></div>';
    modalBody.innerHTML = html;
}

/**
 * 显示错误信息
 */
function showError(message) {
    const errorDisplay = document.getElementById('errorDisplay');
    const errorMessage = document.getElementById('errorMessage');
    errorMessage.textContent = message;
    errorDisplay.style.display = 'block';
}
