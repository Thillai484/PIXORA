/**
 * Pixora Studio Customization Controller
 * Handles preset selection, style customization, pack mode toggle, and generation submission.
 */
document.addEventListener('DOMContentLoaded', async () => {
    // Auth Check
    const token = await requireAuth();
    if (!token) return;

    // Elements
    const tabOfficial = document.getElementById('tab-official');
    const tabProfessional = document.getElementById('tab-professional');
    const viewOfficial = document.getElementById('view-official');
    const viewProfessional = document.getElementById('view-professional');
    const modeSwitcher = document.getElementById('mode-switcher');
    const generateBtn = document.getElementById('btn-generate');
    const btnText = document.getElementById('btn-text');
    const btnSpinner = document.getElementById('btn-spinner');
    const alertBox = document.getElementById('customize-alert');
    const sidebarPhotoImg = document.getElementById('sidebar-photo-img');
    const sidebarStatus = document.getElementById('sidebar-status');

    // Pack Mode Elements
    const togglePackMode = document.getElementById('toggle-pack-mode');
    const packCountPill = document.getElementById('pack-count-pill');
    const packCheckIndicators = document.querySelectorAll('.pack-check-indicator');

    // Query Params
    const urlParams = new URLSearchParams(window.location.search);
    const photoId = urlParams.get('photoId');

    if (!photoId) {
        window.location.href = 'upload.html';
        return;
    }

    // State Variables
    let isPackMode = false;
    let selectedPackTypes = new Set(['RESUME', 'PASSPORT']); // Default 2 items in pack
    let currentMode = 'OFFICIAL';
    let currentPurpose = 'RESUME';
    let currentStyle = 'CORPORATE';
    let currentClothing = 'BLAZER';
    let currentBackground = 'OFFICE';

    // Populate Sidebar Preview
    try {
        const localPreview = sessionStorage.getItem('pixora_local_preview');
        if (localPreview) {
            sidebarPhotoImg.src = localPreview;
        } else {
            const photo = await getPhotoDetails(photoId);
            if (photo && photo.originalImageUrl) {
                sidebarPhotoImg.src = photo.originalImageUrl;
            }
        }
        sidebarStatus.textContent = `Photo #${photoId} Ready`;
    } catch (err) {
        console.warn('Could not load photo thumbnail:', err);
    }

    // Mode Toggle Tabs (Single Mode)
    tabOfficial.addEventListener('click', () => {
        if (isPackMode) return;
        currentMode = 'OFFICIAL';
        tabOfficial.classList.add('active');
        tabProfessional.classList.remove('active');
        viewOfficial.style.display = 'block';
        viewProfessional.style.display = 'none';
    });

    tabProfessional.addEventListener('click', () => {
        if (isPackMode) return;
        currentMode = 'PROFESSIONAL';
        tabProfessional.classList.add('active');
        tabOfficial.classList.remove('active');
        viewOfficial.style.display = 'none';
        viewProfessional.style.display = 'block';
    });

    // Preset Cards Click Selection
    const presetCards = document.querySelectorAll('.preset-card');
    const visaCountryContainer = document.getElementById('visa-country-container');
    const visaCountrySelect = document.getElementById('visa-country-select');

    presetCards.forEach(card => {
        card.addEventListener('click', () => {
            const type = card.getAttribute('data-type');

            if (isPackMode) {
                // Multi-select in Pack Mode
                if (selectedPackTypes.has(type)) {
                    if (selectedPackTypes.size > 1) {
                        selectedPackTypes.delete(type);
                        card.classList.remove('active');
                        const check = card.querySelector('.pack-check-indicator');
                        if (check) check.style.display = 'none';
                    } else {
                        showAlert('Please select at least one photo style for the pack.');
                    }
                } else {
                    selectedPackTypes.add(type);
                    card.classList.add('active');
                    const check = card.querySelector('.pack-check-indicator');
                    if (check) check.style.display = 'block';
                }

                updatePackUI();
            } else {
                // Single Select in Normal Mode
                presetCards.forEach(c => c.classList.remove('active'));
                card.classList.add('active');
                currentPurpose = type;

                // Show country dropdown for Visa preset
                if (currentPurpose === 'VISA') {
                    visaCountryContainer.style.display = 'flex';
                } else {
                    visaCountryContainer.style.display = 'none';
                }
            }
        });
    });

    // Pack Mode Switcher Listener
    if (togglePackMode) {
        togglePackMode.addEventListener('change', () => {
            isPackMode = togglePackMode.checked;
            hideAlert();

            if (isPackMode) {
                // Activate Pack Mode
                modeSwitcher.style.display = 'none';
                viewOfficial.style.display = 'block';
                viewProfessional.style.display = 'none';
                packCountPill.style.display = 'inline-block';

                // Sync active card states to selectedPackTypes
                presetCards.forEach(card => {
                    const type = card.getAttribute('data-type');
                    const check = card.querySelector('.pack-check-indicator');
                    if (selectedPackTypes.has(type)) {
                        card.classList.add('active');
                        if (check) check.style.display = 'block';
                    } else {
                        card.classList.remove('active');
                        if (check) check.style.display = 'none';
                    }
                });

                updatePackUI();
            } else {
                // Return to Single Preset Mode
                modeSwitcher.style.display = 'flex';
                packCountPill.style.display = 'none';
                packCheckIndicators.forEach(ind => ind.style.display = 'none');

                presetCards.forEach(card => {
                    const type = card.getAttribute('data-type');
                    if (type === currentPurpose) {
                        card.classList.add('active');
                    } else {
                        card.classList.remove('active');
                    }
                });

                btnText.textContent = '✨ Generate Professional Portrait';
            }
        });
    }

    function updatePackUI() {
        const count = selectedPackTypes.size;
        packCountPill.textContent = `${count} Selected`;
        btnText.textContent = `✨ Generate Photo Pack (${count} Photos)`;

        if (selectedPackTypes.has('VISA')) {
            visaCountryContainer.style.display = 'flex';
        } else {
            visaCountryContainer.style.display = 'none';
        }
    }

    // Option Chips Click Selection (Professional Mode)
    const chipGroups = document.querySelectorAll('.chip-group');
    chipGroups.forEach(group => {
        const category = group.getAttribute('data-category');
        const chips = group.querySelectorAll('.option-chip');

        chips.forEach(chip => {
            chip.addEventListener('click', () => {
                chips.forEach(c => c.classList.remove('active'));
                chip.classList.add('active');
                const val = chip.getAttribute('data-value');

                if (category === 'style') currentStyle = val;
                if (category === 'clothing') currentClothing = val;
                if (category === 'background') currentBackground = val;
            });
        });
    });

    // Generate Photo Submit Handler
    generateBtn.addEventListener('click', async () => {
        try {
            hideAlert();
            generateBtn.disabled = true;
            btnText.style.display = 'none';
            btnSpinner.style.display = 'inline-block';

            if (isPackMode) {
                // BATCH PACK GENERATION (POST /api/photos/generate-pack)
                const typesArray = Array.from(selectedPackTypes);
                const packPayload = {
                    photoId: parseInt(photoId, 10),
                    types: typesArray,
                    country: visaCountrySelect ? visaCountrySelect.value : 'US',
                    sharedOptions: {
                        style: currentStyle,
                        clothing: currentClothing,
                        background: currentBackground
                    }
                };

                const response = await fetch(`${API_CONFIG.BASE_URL}/photos/generate-pack`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'Authorization': `Bearer ${token}`
                    },
                    body: JSON.stringify(packPayload)
                });

                if (!response.ok) {
                    const errJson = await response.json().catch(() => ({}));
                    throw new Error(errJson.message || `Pack generation failed (HTTP ${response.status})`);
                }

                const packResult = await response.json();
                sessionStorage.setItem('pixora_pack_data', JSON.stringify(packResult));
                sessionStorage.setItem('pixora_current_pack_id', packResult.packId);

                // Redirect to pack result page
                window.location.href = `pack-result.html?packId=${packResult.packId}&photoId=${photoId}`;

            } else {
                // SINGLE PHOTO GENERATION (Existing Flow)
                const payload = {
                    photoId: photoId,
                    mode: currentMode,
                    photoType: currentMode === 'OFFICIAL' ? currentPurpose : 'PROFESSIONAL_CUSTOM',
                    country: (currentMode === 'OFFICIAL' && currentPurpose === 'VISA' && visaCountrySelect) ? visaCountrySelect.value : 'US',
                    style: currentMode === 'OFFICIAL' ? null : currentStyle,
                    clothing: currentMode === 'OFFICIAL' ? null : currentClothing,
                    background: currentMode === 'OFFICIAL' ? null : currentBackground
                };

                const updatedPhoto = await customizePhoto(payload);

                sessionStorage.setItem('pixora_customization', JSON.stringify(payload));
                sessionStorage.setItem('pixora_current_photo_id', photoId);

                window.location.href = `result.html?photoId=${photoId}`;
            }
        } catch (error) {
            console.error('Generation submission failed:', error);
            showAlert(error.message || 'Failed to submit generation. Please try again.');
            generateBtn.disabled = false;
            btnText.style.display = 'inline';
            btnSpinner.style.display = 'none';
        }
    });

    function showAlert(msg) {
        alertBox.textContent = msg;
        alertBox.style.display = 'block';
    }

    function hideAlert() {
        alertBox.style.display = 'none';
        alertBox.textContent = '';
    }
});
