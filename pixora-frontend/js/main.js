import { checkBackendHealth } from './api.js';

document.addEventListener('DOMContentLoaded', async () => {
    const statusBadge = document.getElementById('backend-status');
    if (!statusBadge) return;

    try {
        const data = await checkBackendHealth();
        if (data && data.status === 'ok') {
            statusBadge.textContent = 'Backend connected ✓';
            statusBadge.className = 'status-badge connected';
        } else {
            statusBadge.textContent = 'Backend response unexpected';
            statusBadge.className = 'status-badge disconnected';
        }
    } catch (err) {
        statusBadge.textContent = 'Backend disconnected ✗ (Start Spring Boot on port 8080)';
        statusBadge.className = 'status-badge disconnected';
    }
});
