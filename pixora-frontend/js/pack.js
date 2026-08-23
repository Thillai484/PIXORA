/**
 * Pixora Photo Pack Controller
 * Manages Batch Studio Pack generation, parallel status polling, and ZIP archive download
 */
import { generatePhotoPack, getPhotoStatus, BASE_URL } from './api.js';

document.addEventListener('DOMContentLoaded', async () => {
    const urlParams = new URLSearchParams(window.location.search);
    const photoIdParam = urlParams.get('photoId') || sessionStorage.getItem('pixora_current_photo_id');

    const viewPackSelect = document.getElementById('view-pack-select');
    const viewPackProgress = document.getElementById('view-pack-progress');
    const viewPackResults = document.getElementById('view-pack-results');

    const btnStartPack = document.getElementById('btn-start-pack');
    const packBtnText = document.getElementById('pack-btn-text');
    const packBtnSpinner = document.getElementById('pack-btn-spinner');

    const packProgressBar = document.getElementById('pack-progress-bar');
    const packProgressItems = document.getElementById('pack-progress-items');
    const packResultsGrid = document.getElementById('pack-results-grid');

    const btnDownloadZip = document.getElementById('btn-download-zip');
    const zipBtnText = document.getElementById('zip-btn-text');
    const zipBtnSpinner = document.getElementById('zip-btn-spinner');

    let selectedPack = 'PROFESSIONAL_PACK';
    let generatedPhotoIds = [];
    let pollInterval = null;

    if (!photoIdParam) {
        window.location.href = 'upload.html';
        return;
    }

    const photoId = parseInt(photoIdParam, 10);

    // Pack Cards Selection
    const packCards = document.querySelectorAll('[data-pack]');
    packCards.forEach(card => {
        card.addEventListener('click', () => {
            packCards.forEach(c => c.classList.remove('active'));
            card.classList.add('active');
            selectedPack = card.getAttribute('data-pack');
        });
    });

    // Start Pack Generation
    btnStartPack.addEventListener('click', async () => {
        try {
            btnStartPack.disabled = true;
            packBtnText.style.display = 'none';
            packBtnSpinner.style.display = 'inline-block';

            const response = await generatePhotoPack(photoId, selectedPack);
            generatedPhotoIds = response.generatedPhotoIds || [];

            viewPackSelect.style.display = 'none';
            viewPackProgress.style.display = 'block';

            initProgressTrackers(response.photos || []);
            startParallelPolling();

        } catch (error) {
            console.error('Failed to start photo pack:', error);
            alert(error.message || 'Failed to start batch generation.');
            btnStartPack.disabled = false;
            packBtnText.style.display = 'inline';
            packBtnSpinner.style.display = 'none';
        }
    });

    function initProgressTrackers(photos) {
        packProgressItems.innerHTML = '';
        photos.forEach(photo => {
            const item = document.createElement('div');
            item.className = 'tracker-item active';
            item.id = `tracker-${photo.id}`;
            item.innerHTML = `
                <span class="tracker-dot"></span>
                <span>Rendering ${photo.photoType.replace('_', ' ')} (${photo.style || 'STUDIO'})...</span>
            `;
            packProgressItems.appendChild(item);
        });
    }

    function startParallelPolling() {
        let completedCount = 0;
        const total = generatedPhotoIds.length;

        pollInterval = setInterval(async () => {
            try {
                const statuses = await Promise.all(
                    generatedPhotoIds.map(id => getPhotoStatus(id))
                );

                completedCount = 0;
                statuses.forEach(st => {
                    const trackerEl = document.getElementById(`tracker-${st.photoId}`);
                    if (st.status === 'DONE') {
                        completedCount++;
                        if (trackerEl) trackerEl.className = 'tracker-item done';
                    } else if (st.status === 'FAILED') {
                        if (trackerEl) {
                            trackerEl.className = 'tracker-item';
                            trackerEl.style.color = '#ef4444';
                        }
                    }
                });

                const pct = Math.max(25, Math.round((completedCount / total) * 100));
                packProgressBar.style.width = `${pct}%`;

                if (completedCount === total && total > 0) {
                    clearInterval(pollInterval);
                    renderPackResults(statuses);
                }

            } catch (err) {
                console.warn('Pack polling warning:', err);
            }
        }, 1800);
    }

    function renderPackResults(photos) {
        viewPackProgress.style.display = 'none';
        viewPackResults.style.display = 'block';
        packResultsGrid.innerHTML = '';

        photos.forEach(photo => {
            const card = document.createElement('div');
            card.className = 'gallery-card';
            card.innerHTML = `
                <div class="card-image-box">
                    <img src="${photo.generatedImageUrl}" alt="${photo.photoType}" class="card-image">
                    <div class="card-overlay">
                        <a href="result.html?photoId=${photo.photoId}" class="card-action-btn btn-overlay-view">
                            <span>👁️ Compare</span>
                        </a>
                        <a href="${photo.generatedImageUrl}" target="_blank" download="pixora-${photo.photoId}.png" class="card-action-btn btn-overlay-down">
                            <span>📥 Download</span>
                        </a>
                    </div>
                </div>
                <div class="card-info">
                    <div class="card-title-row">
                        <span class="card-title">${photo.photoType.replace('_', ' ')}</span>
                        <span class="status-tag done">Ready</span>
                    </div>
                    <div class="card-meta-line">
                        <span>${photo.style || 'STUDIO'} • ${photo.clothing || 'FORMAL'}</span>
                        <span>8K UHD</span>
                    </div>
                </div>
            `;
            packResultsGrid.appendChild(card);
        });
    }

    // ZIP Download Handler
    btnDownloadZip.addEventListener('click', async () => {
        try {
            btnDownloadZip.disabled = true;
            zipBtnText.style.display = 'none';
            zipBtnSpinner.style.display = 'inline-block';

            const token = sessionStorage.getItem('pixora_token');
            const idParams = generatedPhotoIds.join(',');
            const zipUrl = `${BASE_URL}/photos/pack/zip?ids=${idParams}`;

            const headers = {};
            if (token) {
                headers['Authorization'] = `Bearer ${token}`;
            }

            const res = await fetch(zipUrl, { headers });
            if (!res.ok) throw new Error('ZIP bundling failed');

            const blob = await res.blob();
            const blobUrl = window.URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = blobUrl;
            link.download = `pixora-studio-pack-${Date.now()}.zip`;
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            window.URL.revokeObjectURL(blobUrl);

            btnDownloadZip.disabled = false;
            zipBtnText.style.display = 'inline';
            zipBtnSpinner.style.display = 'none';

        } catch (err) {
            console.error('ZIP download error:', err);
            alert('Could not download ZIP archive. You can download individual photos from the cards.');
            btnDownloadZip.disabled = false;
            zipBtnText.style.display = 'inline';
            zipBtnSpinner.style.display = 'none';
        }
    });
});
