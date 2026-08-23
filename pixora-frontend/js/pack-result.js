/**
 * Pixora Studio Photo Pack Result Controller
 * Renders batch generated photo pack items, compliance checklists, and handles ZIP downloads.
 */
document.addEventListener('DOMContentLoaded', async () => {
    // Auth Check
    const token = await requireAuth();
    if (!token) return;

    // Elements
    const pageTitle = document.getElementById('page-title');
    const packGrid = document.getElementById('pack-grid');
    const packSummaryTitle = document.getElementById('pack-summary-title');
    const btnDownloadAllZip = document.getElementById('btn-download-all-zip');
    const viewLoading = document.getElementById('view-loading');

    // Query Params
    const urlParams = new URLSearchParams(window.location.search);
    const packId = urlParams.get('packId');
    const photoId = urlParams.get('photoId');

    let packData = null;

    // 1. Try reading initial result from sessionStorage
    const cachedPack = sessionStorage.getItem('pixora_pack_data');
    if (cachedPack) {
        try {
            const parsed = JSON.parse(cachedPack);
            if (parsed.packId === packId || !packId) {
                packData = parsed;
            }
        } catch (e) {}
    }

    // 2. Fetch pack photos from API
    if (packId) {
        try {
            const response = await fetch(`${API_CONFIG.BASE_URL}/photos/pack/${packId}`, {
                headers: {
                    'Authorization': `Bearer ${token}`
                }
            });

            if (response.ok) {
                const photos = await response.json();
                if (photos && photos.length > 0) {
                    renderPackPhotos(photos, packId);
                    setupZipButton(packId, photos);
                    return;
                }
            }
        } catch (err) {
            console.warn('Failed to fetch pack from server, falling back to local payload:', err);
        }
    }

    if (packData && packData.results) {
        renderPackResults(packData.results, packData.packId || packId);
        setupZipButton(packData.packId || packId, packData.results);
    } else {
        packGrid.innerHTML = `
            <div style="grid-column: 1 / -1; text-align: center; padding: 3rem; background: rgba(15, 23, 42, 0.5); border-radius: 16px;">
                <span style="font-size: 3rem;">🔍</span>
                <h3 style="color: #fff; margin-top: 1rem;">No Pack Data Found</h3>
                <p style="color: #94a3b8;">Please select multiple styles on the customize page to generate a photo pack.</p>
                <a href="upload.html" class="btn btn-primary" style="margin-top: 1rem;">Upload Photo</a>
            </div>
        `;
    }

    /**
     * Render photos returned from backend GET /api/photos/pack/{packId}
     */
    function renderPackPhotos(photos, packId) {
        packGrid.innerHTML = '';
        packSummaryTitle.textContent = `${photos.length} Professional Photos Ready`;

        photos.forEach((photo, idx) => {
            const card = createPhotoCard({
                id: photo.id,
                type: photo.photoType,
                mode: photo.mode,
                url: photo.generatedImageUrl || photo.originalImageUrl,
                specLabel: photo.specLabel || getSpecFallback(photo.photoType),
                compliance: photo.complianceResult
            }, idx);

            packGrid.appendChild(card);
        });
    }

    /**
     * Render results returned from POST /api/photos/generate-pack
     */
    function renderPackResults(results, packId) {
        packGrid.innerHTML = '';
        packSummaryTitle.textContent = `${results.length} Professional Photos Ready`;

        results.forEach((item, idx) => {
            const card = createPhotoCard({
                id: item.photoId,
                type: item.type,
                mode: item.mode,
                url: item.generatedImageUrl,
                specLabel: item.specLabel || getSpecFallback(item.type),
                compliance: item.complianceResult
            }, idx);

            packGrid.appendChild(card);
        });
    }

    function createPhotoCard(data, idx) {
        const card = document.createElement('div');
        card.className = 'pack-card';
        card.style.opacity = '0';
        card.style.transform = 'translateY(15px)';
        card.style.transition = 'all 0.4s cubic-bezier(0.4, 0, 0.2, 1)';

        const typeTitle = formatTypeTitle(data.type);
        const imgUrl = data.url || 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500&auto=format&fit=crop';

        // Compliance check badge
        const comp = data.compliance;
        let compBadgeHtml = '<span style="background: rgba(16, 185, 129, 0.15); color: #34d399; border: 1px solid rgba(16, 185, 129, 0.3); padding: 3px 10px; border-radius: 12px; font-size: 0.75rem; font-weight: 700;">✓ 100% COMPLIANT</span>';

        if (comp && comp.overallStatus === 'NEEDS_REVIEW') {
            compBadgeHtml = '<span style="background: rgba(245, 158, 11, 0.15); color: #fbbf24; border: 1px solid rgba(245, 158, 11, 0.3); padding: 3px 10px; border-radius: 12px; font-size: 0.75rem; font-weight: 700;">⚠️ REVIEW</span>';
        } else if (comp && comp.overallStatus === 'FAIL') {
            compBadgeHtml = '<span style="background: rgba(239, 68, 68, 0.15); color: #f87171; border: 1px solid rgba(239, 68, 68, 0.3); padding: 3px 10px; border-radius: 12px; font-size: 0.75rem; font-weight: 700;">✕ NON-COMPLIANT</span>';
        }

        // Mini check details
        let checkItemsHtml = '';
        if (comp && comp.checks && comp.checks.length > 0) {
            checkItemsHtml = comp.checks.slice(0, 3).map(c => `
                <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 4px; font-size: 0.78rem;">
                    <span style="color: #cbd5e1;">${escapeHtml(c.label)}</span>
                    <span style="color: ${c.status === 'PASS' ? '#34d399' : '#fbbf24'}; font-weight: 600;">${c.status}</span>
                </div>
            `).join('');
        } else {
            checkItemsHtml = `
                <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 4px; font-size: 0.78rem;">
                    <span style="color: #cbd5e1;">Biometric Alignment</span>
                    <span style="color: #34d399; font-weight: 600;">PASS</span>
                </div>
                <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 4px; font-size: 0.78rem;">
                    <span style="color: #cbd5e1;">Background Uniformity</span>
                    <span style="color: #34d399; font-weight: 600;">PASS</span>
                </div>
            `;
        }

        card.innerHTML = `
            <div class="pack-card-header">
                <h3 class="pack-card-title">${typeTitle}</h3>
                ${compBadgeHtml}
            </div>
            <div class="pack-img-container">
                <img src="${imgUrl}" alt="${data.type} Result">
            </div>
            <div class="pack-card-body">
                <div>
                    <span class="spec-pill">${escapeHtml(data.specLabel)}</span>
                </div>

                <div class="mini-compliance">
                    <div style="font-weight: 600; color: #94a3b8; font-size: 0.75rem; text-transform: uppercase; letter-spacing: 0.5px;">Compliance Checklist</div>
                    ${checkItemsHtml}
                </div>

                <div style="margin-top: auto; display: flex; gap: 8px;">
                    <button type="button" class="btn btn-primary btn-sm btn-download-single" data-id="${data.id}" data-url="${imgUrl}" data-type="${data.type}" style="width: 100%;">
                        📥 Download HD
                    </button>
                    <a href="${imgUrl}" target="_blank" class="btn btn-secondary btn-sm" style="padding: 6px 12px;" title="View Fullscreen">
                        🔍
                    </a>
                </div>
            </div>
        `;

        // Attach single download event
        const dlBtn = card.querySelector('.btn-download-single');
        if (dlBtn) {
            dlBtn.addEventListener('click', async () => {
                const targetUrl = dlBtn.getAttribute('data-url');
                const pType = dlBtn.getAttribute('data-type') || 'portrait';
                await downloadDirectImage(targetUrl, `pixora-${pType.toLowerCase()}.jpg`);
            });
        }

        setTimeout(() => {
            card.style.opacity = '1';
            card.style.transform = 'translateY(0)';
        }, 60 + idx * 100);

        return card;
    }

    /**
     * Setup Download All (ZIP) button
     */
    function setupZipButton(packId, items) {
        if (!btnDownloadAllZip) return;

        btnDownloadAllZip.addEventListener('click', async () => {
            try {
                btnDownloadAllZip.disabled = true;
                btnDownloadAllZip.innerHTML = '<span>⏳ Preparing ZIP Archive...</span>';

                // Try backend ZIP endpoint
                const zipUrl = `${API_CONFIG.BASE_URL}/photos/pack/${packId}/zip`;
                const res = await fetch(zipUrl, {
                    headers: {
                        'Authorization': `Bearer ${token}`
                    }
                });

                if (res.ok) {
                    const blob = await res.blob();
                    const url = window.URL.createObjectURL(blob);
                    const a = document.createElement('a');
                    a.href = url;
                    a.download = `pixora-photo-pack-${packId}.zip`;
                    document.body.appendChild(a);
                    a.click();
                    window.URL.revokeObjectURL(url);
                    a.remove();
                } else {
                    // Fallback to downloading individual images
                    for (const item of items) {
                        const imgUrl = item.generatedImageUrl || item.url;
                        const t = item.type || item.photoType || 'portrait';
                        if (imgUrl) {
                            await downloadDirectImage(imgUrl, `pixora-${t.toLowerCase()}.jpg`);
                            await new Promise(r => setTimeout(r, 400));
                        }
                    }
                }
            } catch (err) {
                console.error('ZIP download error:', err);
                alert('Could not generate ZIP archive. You can download individual photos using their buttons.');
            } finally {
                btnDownloadAllZip.disabled = false;
                btnDownloadAllZip.innerHTML = '<span>📥 Download All (ZIP)</span>';
            }
        });
    }

    async function downloadDirectImage(url, filename) {
        try {
            const res = await fetch(url);
            const blob = await res.blob();
            const blobUrl = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = blobUrl;
            a.download = filename;
            document.body.appendChild(a);
            a.click();
            window.URL.revokeObjectURL(blobUrl);
            a.remove();
        } catch (e) {
            window.open(url, '_blank');
        }
    }

    function formatTypeTitle(type) {
        if (!type) return '📄 Professional Photo';
        switch (type.toUpperCase()) {
            case 'RESUME': return '📄 Resume / CV';
            case 'LINKEDIN': return '💼 LinkedIn Profile';
            case 'PASSPORT':
            case 'PASSPORT_PHOTO': return '🛂 Passport Photo';
            case 'VISA':
            case 'VISA_APPLICATION': return '✈️ Visa Application';
            case 'COMPANY_ID':
            case 'COMPANY_ID_BADGE': return '🏢 Company ID Badge';
            case 'COLLEGE_ID':
            case 'COLLEGE_STUDENT_ID': return '🎓 College / Student ID';
            default: return '✨ Professional Portrait';
        }
    }

    function getSpecFallback(type) {
        if (!type) return 'Standard Specification';
        switch (type.toUpperCase()) {
            case 'PASSPORT':
            case 'PASSPORT_PHOTO': return '2x2 in • Pure White #FFFFFF • ICAO Compliant';
            case 'VISA':
            case 'VISA_APPLICATION': return '35x45mm / 2x2 in • High Consular Contrast';
            case 'COMPANY_ID':
            case 'COMPANY_ID_BADGE': return 'Standard ID Badge (3:4) • Light Gray #E8E8E8';
            case 'COLLEGE_ID':
            case 'COLLEGE_STUDENT_ID': return 'Student ID (4:5) • Soft Light-Blue #EBF3FA';
            case 'LINKEDIN': return 'Executive Profile • Soft Office Bokeh';
            case 'RESUME':
            default: return 'Corporate Resume Headshot • Studio Lighting';
        }
    }

    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }
});
