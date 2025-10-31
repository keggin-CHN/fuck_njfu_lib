/**
 * 实时流量页面脚本
 */

// 页面加载时获取数据
document.addEventListener('DOMContentLoaded', function() {
    loadTrafficData();
});

/**
 * 加载流量数据
 */
async function loadTrafficData() {
    const loadingIndicator = document.getElementById('loadingIndicator');
    const trafficDisplay = document.getElementById('trafficDisplay');
    const errorDisplay = document.getElementById('errorDisplay');
    const refreshBtn = document.getElementById('refreshBtn');

    // 显示加载状态
    loadingIndicator.style.display = 'block';
    trafficDisplay.style.display = 'none';
    errorDisplay.style.display = 'none';
    refreshBtn.disabled = true;

    try {
        const response = await fetch(`${window.CONFIG.API_BASE_URL}/api/traffic`);
        const data = await response.json();

        if (data.success) {
            displayTrafficData(data);
            loadingIndicator.style.display = 'none';
            trafficDisplay.style.display = 'block';
        } else {
            showError(data.message || '获取流量数据失败');
            loadingIndicator.style.display = 'none';
        }
    } catch (error) {
        console.error('Error:', error);
        showError('网络请求失败，请检查配置或稍后重试');
        loadingIndicator.style.display = 'none';
    } finally {
        refreshBtn.disabled = false;
    }
}

/**
 * 显示流量数据
 */
function displayTrafficData(data) {
    document.getElementById('currentCount').textContent = data.current_count || 0;
    document.getElementById('totalCapacity').textContent = data.total_capacity || 0;
    document.getElementById('percentage').textContent = (data.percentage || 0) + '%';
    document.getElementById('updateTime').textContent = data.updated_at || '-';

    // 更新进度条
    const progressBar = document.getElementById('progressBar');
    const percentage = data.percentage || 0;
    progressBar.style.width = percentage + '%';
    progressBar.setAttribute('aria-valuenow', percentage);
    progressBar.querySelector('span').textContent = percentage + '%';

    // 根据占用率改变进度条颜色
    progressBar.className = 'progress-bar progress-bar-striped progress-bar-animated';
    if (percentage < 50) {
        progressBar.classList.add('bg-success');
    } else if (percentage < 80) {
        progressBar.classList.add('bg-warning');
    } else {
        progressBar.classList.add('bg-danger');
    }
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
