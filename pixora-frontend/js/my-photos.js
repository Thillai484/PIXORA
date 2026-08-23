/**
 * Pixora My Photos Gallery Controller
 * Handles loading user collection, client-side filter/sort, and delete actions with confirmation modal
 */
import { getMyPhotos, deletePhoto } from './api.js';

document.addEventListener('DOMContentLoaded', async () => {
    const galleryGrid = document.getElementById('gallery-grid');
    const galleryLoader = document.getElementById('gallery-loader');
    const galleryEmpty = document.getElementById('gallery-empty');

    const countAll = document.getElementById('count-all');
    const countOfficial = document.getElementById('count-official');
    const countProfessional = document.getElementById('count-professional');

    const filterBtns = document.querySelectorAll('.filter-btn');
    const sortSelect = document.getElementById('sort-select');

    const deleteModal = document.getElementById('delete-modal');
    const btnModalCancel = document.getElementById('btn-modal-cancel');
    const btnModalConfirm = document.getElementById('btn-modal-confirm');
    const delBtnText = document.getElementById('del-btn-text');
    const delBtnSpinner = document.getElementById('del-btn-spinner');

    let allPhotos = [];
    let currentFilter = 'all';
    let currentSort = 'desc';
    let photoToDeleteId = null;

    // Load Gallery
    await loadGallery();

    async function loadGallery() {
        try {
            galleryLoader.style.display = 'block';
            galleryGrid.style.display = 'none';
            galleryEmpty.style.display = 'none';

            allPhotos = await getMyPhotos();

            updateCounts();
            renderGallery();
        } catch (error) {
            console.error('Failed to load user photos:', error);
            galleryLoader.style.display = 'none';
            galleryEmpty.style.display = 'block';
        }
    }

    function updateCounts() {
        const total = allPhotos.length;
        const official = allPhotos.filter(p => (p.mode || 'OFFICIAL').toUpperCase() === 'OFFICIAL').length;
        const prof = allPhotos.filter(p => (p.mode || '').toUpperCase() === 'PROFESSIONAL').length;

        countAll.textContent = total;
        countOfficial.textContent = official;
        countProfessional.textContent = prof;
    }

    function renderGallery() {
        galleryLoader.style.display = 'none';

        if (!allPhotos || allPhotos.length === 0) {
            galleryGrid.style.display = 'none';
            galleryEmpty.style.display = 'block';
            return;
        }

        // Apply Filter
        let filtered = [...allPhotos];
        if (currentFilter !== 'all') {
            filtered = filtered.filter(p => (p.mode || 'OFFICIAL').toUpperCase() === currentFilter);
        }

        // Apply Sort
        filtered.sort((a, b) => {
            const dateA = new Date(a.createdAt || 0).getTime();
            const dateB = new Date(b.createdAt || 0).getTime();
            return currentSort === 'desc' ? dateB - dateA : dateA - dateB;
        });

        if (filtered.length === 0) {
            galleryGrid.innerHTML = `
                <div style="grid-column: 1 / -1; text-align: center; padding: 3rem 1rem; color: var(--text-secondary);">
                    No portraits found matching this filter category.
                </div>
            `;
            galleryGrid.style.display = 'grid';
            galleryEmpty.style.display = 'none';
            return;
        }

        galleryEmpty.style.display = 'none';
        galleryGrid.style.display = 'grid';
        galleryGrid.innerHTML = '';

        filtered.forEach(photo => {
            const card = createPhotoCard(photo);
            galleryGrid.appendChild(card);
        });
    }

    function createPhotoCard(photo) {
        const card = document.createElement('div');
        card.className = 'gallery-card';
        card.id = `photo-card-${photo.id}`;

        const isDone = photo.status === 'DONE';
        const displayImage = isDone && photo.generatedImageUrl ? photo.generatedImageUrl : photo.originalImageUrl;
        const purposeLabel = (photo.photoType || 'Studio').replace('_', ' ');
        const modeLabel = (photo.mode || 'Official').toUpperCase();

        const formattedDate = photo.createdAt 
            ? new Date(photo.createdAt).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
            : 'Recently created';

        let statusClass = 'processing';
        let statusText = 'Processing';
        if (photo.status === 'DONE') {
            statusClass = 'done';
            statusText = 'Ready';
        } else if (photo.status === 'FAILED') {
            statusClass = 'failed';
            statusText = 'Failed';
        } else if (photo.status === 'UPLOADED' || photo.status === 'CONFIGURED') {
            statusClass = 'processing';
            statusText = 'Configured';
        }

        card.innerHTML = `
            <div class="card-image-box">
                <img src="${displayImage}" alt="${purposeLabel}" class="card-image" loading="lazy">
                <div class="card-overlay">
                    <a href="result.html?photoId=${photo.id}" class="card-action-btn btn-overlay-view">
                        <span>👁️ View & Compare</span>
                    </a>
                    ${isDone && photo.generatedImageUrl ? `
                        <button type="button" class="card-action-btn btn-overlay-down" data-download-id="${photo.id}" data-url="${photo.generatedImageUrl}">
                            <span>📥 Download HD</span>
                        </button>
                    ` : ''}
                    <button type="button" class="card-action-btn btn-overlay-del" data-delete-id="${photo.id}">
                        <span>🗑️ Delete</span>
                    </button>
                </div>
            </div>
            <div class="card-info">
                <div class="card-title-row">
                    <span class="card-title">${purposeLabel}</span>
                    <span class="status-tag ${statusClass}">${statusText}</span>
                </div>
                <div class="card-meta-line">
                    <span>${modeLabel}</span>
                    <span>${formattedDate}</span>
                </div>
            </div>
        `;

        // Attach Card Download Listener
        const btnDown = card.querySelector('[data-download-id]');
        if (btnDown) {
            btnDown.addEventListener('click', async (e) => {
                e.stopPropagation();
                const url = btnDown.getAttribute('data-url');
                try {
                    const res = await fetch(url);
                    const blob = await res.blob();
                    const blobUrl = window.URL.createObjectURL(blob);
                    const link = document.createElement('a');
                    link.href = blobUrl;
                    link.download = `pixora-${photo.id}.png`;
                    document.body.appendChild(link);
                    link.click();
                    document.body.removeChild(link);
                    window.URL.revokeObjectURL(blobUrl);
                } catch {
                    window.open(url, '_blank');
                }
            });
        }

        // Attach Card Delete Listener
        const btnDel = card.querySelector('[data-delete-id]');
        if (btnDel) {
            btnDel.addEventListener('click', (e) => {
                e.stopPropagation();
                openDeleteModal(photo.id);
            });
        }

        return card;
    }

    // Filter Button Clicks
    filterBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            filterBtns.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            currentFilter = btn.getAttribute('data-filter');
            renderGallery();
        });
    });

    // Sort Dropdown Change
    sortSelect.addEventListener('change', () => {
        currentSort = sortSelect.value;
        renderGallery();
    });

    // Modal Handlers
    function openDeleteModal(photoId) {
        photoToDeleteId = photoId;
        deleteModal.classList.add('active');
    }

    function closeDeleteModal() {
        deleteModal.classList.remove('active');
        photoToDeleteId = null;
        delBtnText.style.display = 'inline';
        delBtnSpinner.style.display = 'none';
        btnModalConfirm.disabled = false;
    }

    btnModalCancel.addEventListener('click', closeDeleteModal);
    deleteModal.addEventListener('click', (e) => {
        if (e.target === deleteModal) closeDeleteModal();
    });

    // Confirm Delete Click
    btnModalConfirm.addEventListener('click', async () => {
        if (!photoToDeleteId) return;

        try {
            btnModalConfirm.disabled = true;
            delBtnText.style.display = 'none';
            delBtnSpinner.style.display = 'inline-block';

            await deletePhoto(photoToDeleteId);

            // Remove card from UI with smooth fade
            const cardEl = document.getElementById(`photo-card-${photoToDeleteId}`);
            if (cardEl) {
                cardEl.style.transition = 'opacity 0.3s ease, transform 0.3s ease';
                cardEl.style.opacity = '0';
                cardEl.style.transform = 'scale(0.9)';
                setTimeout(() => {
                    allPhotos = allPhotos.filter(p => p.id !== photoToDeleteId);
                    updateCounts();
                    renderGallery();
                }, 300);
            } else {
                allPhotos = allPhotos.filter(p => p.id !== photoToDeleteId);
                updateCounts();
                renderGallery();
            }

            closeDeleteModal();
        } catch (err) {
            console.error('Failed to delete photo:', err);
            alert('Failed to delete photo. Please try again.');
            closeDeleteModal();
        }
    });
});
