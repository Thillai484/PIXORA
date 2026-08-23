/**
 * Pixora Customize Controller
 * Handles Official Presets vs. Professional Custom selectors and submits configuration
 */
import { getPhoto, customizePhoto } from './api.js';

document.addEventListener('DOMContentLoaded', async () => {
    const urlParams = new URLSearchParams(window.location.search);
    const photoIdParam = urlParams.get('photoId') || sessionStorage.getItem('pixora_current_photo_id');

    const sidebarPhotoImg = document.getElementById('sidebar-photo-img');
    const sidebarStatus = document.getElementById('sidebar-status');
    const alertBox = document.getElementById('customize-alert');
    const tabOfficial = document.getElementById('tab-official');
    const tabProfessional = document.getElementById('tab-professional');
    const viewOfficial = document.getElementById('view-official');
    const viewProfessional = document.getElementById('view-professional');
    const generateBtn = document.getElementById('generate-btn');
    const btnText = document.getElementById('btn-text');
    const btnSpinner = document.getElementById('btn-spinner');

    if (!photoIdParam) {
        window.location.href = 'upload.html';
        return;
    }

    const photoId = parseInt(photoIdParam, 10);

    // Initial State
    let currentMode = 'OFFICIAL';
    let currentPurpose = 'RESUME';
    let currentStyle = 'CORPORATE';
    let currentClothing = 'BLAZER';
    let currentBackground = 'OFFICE';

    // Load photo preview thumbnail
    try {
        const localPreview = sessionStorage.getItem('pixora_local_preview');
        const cachedUrl = sessionStorage.getItem('pixora_current_photo_url');
        
        if (localPreview) {
            sidebarPhotoImg.src = localPreview;
        } else if (cachedUrl) {
            sidebarPhotoImg.src = cachedUrl;
        } else {
            const photo = await getPhoto(photoId);
            if (photo && photo.originalImageUrl) {
                sidebarPhotoImg.src = photo.originalImageUrl;
                sessionStorage.setItem('pixora_current_photo_url', photo.originalImageUrl);
            }
        }
        sidebarStatus.textContent = `Photo #${photoId} Ready`;
    } catch (err) {
        console.warn('Could not load photo thumbnail:', err);
    }

    // Mode Toggle Tabs
    tabOfficial.addEventListener('click', () => {
        currentMode = 'OFFICIAL';
        tabOfficial.classList.add('active');
        tabProfessional.classList.remove('active');
        viewOfficial.style.display = 'block';
        viewProfessional.style.display = 'none';
    });

    tabProfessional.addEventListener('click', () => {
        currentMode = 'PROFESSIONAL';
        tabProfessional.classList.add('active');
        tabOfficial.classList.remove('active');
        viewOfficial.style.display = 'none';
        viewProfessional.style.display = 'block';
    });

    // Preset Cards Click Selection (Official Mode)
    const presetCards = document.querySelectorAll('.preset-card');
    const visaCountryContainer = document.getElementById('visa-country-container');
    const visaCountrySelect = document.getElementById('visa-country-select');

    presetCards.forEach(card => {
        card.addEventListener('click', () => {
            presetCards.forEach(c => c.classList.remove('active'));
            card.classList.add('active');
            currentPurpose = card.getAttribute('data-type');

            // Show country dropdown for Visa preset
            if (currentPurpose === 'VISA') {
                visaCountryContainer.style.display = 'flex';
            } else {
                visaCountryContainer.style.display = 'none';
            }
        });
    });

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

            const payload = {
                photoId: photoId,
                mode: currentMode,
                photoType: currentMode === 'OFFICIAL' ? currentPurpose : 'PROFESSIONAL_CUSTOM',
                country: (currentMode === 'OFFICIAL' && currentPurpose === 'VISA' && visaCountrySelect) ? visaCountrySelect.value : 'US',
                style: currentMode === 'OFFICIAL' ? null : currentStyle,
                clothing: currentMode === 'OFFICIAL' ? null : currentClothing,
                background: currentMode === 'OFFICIAL' ? null : currentBackground
            };

            // Call Backend POST /api/photos/customize
            const updatedPhoto = await customizePhoto(payload);

            // Persist selections for result rendering
            sessionStorage.setItem('pixora_customization', JSON.stringify(payload));
            sessionStorage.setItem('pixora_current_photo_id', photoId);

            // Redirect to result page
            window.location.href = `result.html?photoId=${photoId}`;
        } catch (error) {
            console.error('Customization failed:', error);
            showAlert(error.message || 'Failed to apply photo customization. Please try again.');
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
