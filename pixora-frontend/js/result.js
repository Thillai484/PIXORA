/**
 * Pixora Result Controller
 * Manages Generation Progress Polling and the Interactive Before/After Comparison Slider
 */
import { startGeneration, getPhotoStatus } from './api.js';

document.addEventListener('DOMContentLoaded', async () => {
    const urlParams = new URLSearchParams(window.location.search);
    const photoIdParam = urlParams.get('photoId') || sessionStorage.getItem('pixora_current_photo_id');

    if (!photoIdParam) {
        window.location.href = 'upload.html';
        return;
    }

    const photoId = parseInt(photoIdParam, 10);

    // DOM Elements
    const titleEl = document.getElementById('result-page-title');
    const subtitleEl = document.getElementById('result-page-subtitle');
    const viewLoading = document.getElementById('view-loading');
    const viewResult = document.getElementById('view-result');
    const viewError = document.getElementById('view-error');
    const errorMessageEl = document.getElementById('error-message');
    const btnRetry = document.getElementById('btn-retry');

    // Progress Elements
    const progressBarFill = document.getElementById('progress-bar-fill');
    const loadingStepTitle = document.getElementById('loading-step-title');
    const step1 = document.getElementById('step-1');
    const step2 = document.getElementById('step-2');
    const step3 = document.getElementById('step-3');
    const step4 = document.getElementById('step-4');

    // Result Slider Elements
    const comparisonSlider = document.getElementById('comparison-slider');
    const beforeLayer = document.getElementById('before-layer');
    const sliderHandle = document.getElementById('slider-handle');
    const beforeImg = document.getElementById('before-img');
    const afterImg = document.getElementById('after-img');

    // Metadata Elements
    const metaMode = document.getElementById('meta-mode');
    const metaType = document.getElementById('meta-type');
    const metaClothing = document.getElementById('meta-clothing');
    const metaBg = document.getElementById('meta-bg');

    // Actions
    const btnDownload = document.getElementById('btn-download');
    const btnReCustomize = document.getElementById('btn-re-customize');

    let isPolling = false;
    let pollInterval = null;
    let currentStep = 1;
    let progressTimer = null;
    let currentGeneratedUrl = '';

    // Initialize Page
    btnReCustomize.href = `customize.html?photoId=${photoId}`;
    btnRetry.addEventListener('click', () => {
        startPhotoPipeline();
    });

    // Start Generation / Polling Pipeline
    await startPhotoPipeline();

    async function startPhotoPipeline() {
        showLoadingState();
        startStepAnimator();

        try {
            // Check initial photo status
            const initialStatus = await getPhotoStatus(photoId);

            if (initialStatus.status === 'DONE' && initialStatus.generatedImageUrl) {
                renderResult(initialStatus);
                return;
            }

            if (initialStatus.status !== 'PROCESSING') {
                // Trigger AI generation
                await startGeneration(photoId);
            }

            // Start polling loop
            pollGenerationStatus();

        } catch (error) {
            console.error('Failed to initialize photo pipeline:', error);
            showErrorState(error.message || 'Unable to connect to AI generation studio.');
        }
    }

    function pollGenerationStatus() {
        if (isPolling) return;
        isPolling = true;

        pollInterval = setInterval(async () => {
            try {
                const statusData = await getPhotoStatus(photoId);

                if (statusData.status === 'DONE' && statusData.generatedImageUrl) {
                    clearInterval(pollInterval);
                    clearInterval(progressTimer);
                    isPolling = false;
                    renderResult(statusData);
                } else if (statusData.status === 'FAILED') {
                    clearInterval(pollInterval);
                    clearInterval(progressTimer);
                    isPolling = false;
                    showErrorState(statusData.errorMessage || 'AI generation failed.');
                }
            } catch (err) {
                console.warn('Status poll warning:', err);
            }
        }, 1500);
    }

    function startStepAnimator() {
        currentStep = 1;
        progressBarFill.style.width = '20%';

        const stepTitles = [
            'Analyzing Face Geometry...',
            'Setting Up Studio Lighting & Backdrop...',
            'Synthesizing Tailored Attire & Textures...',
            'Finalizing 8K UHD Photorealistic Portrait...'
        ];

        clearInterval(progressTimer);
        progressTimer = setInterval(() => {
            currentStep++;
            if (currentStep > 4) {
                currentStep = 4;
                progressBarFill.style.width = '90%';
                return;
            }

            loadingStepTitle.textContent = stepTitles[currentStep - 1];
            progressBarFill.style.width = `${currentStep * 24}%`;

            [step1, step2, step3, step4].forEach((s, idx) => {
                if (idx + 1 < currentStep) {
                    s.className = 'tracker-item done';
                } else if (idx + 1 === currentStep) {
                    s.className = 'tracker-item active';
                } else {
                    s.className = 'tracker-item';
                }
            });
        }, 1800);
    }

    function renderResult(photoData) {
        clearInterval(progressTimer);
        clearInterval(pollInterval);

        currentGeneratedUrl = photoData.generatedImageUrl;

        // Set Images
        beforeImg.src = photoData.originalImageUrl;
        afterImg.src = photoData.generatedImageUrl;

        // Ensure before image width matches container for perfect overlay alignment
        updateBeforeImageWidth();
        window.addEventListener('resize', updateBeforeImageWidth);

        // Populate Metadata
        metaMode.textContent = photoData.mode || 'OFFICIAL';
        metaType.textContent = (photoData.photoType || 'STUDIO').replace('_', ' ');
        metaClothing.textContent = (photoData.clothing || 'FORMAL SHIRT').replace('_', ' ');
        metaBg.textContent = (photoData.background || 'STUDIO').replace('_', ' ');

        // Update Page Titles
        titleEl.textContent = 'Your Professional Photo is Ready ✨';
        subtitleEl.textContent = 'Drag the slider to compare your original photo with your new AI studio portrait.';

        // Switch to result view
        viewLoading.style.display = 'none';
        viewError.style.display = 'none';
        viewResult.style.display = 'block';

        // Setup Before/After Slider Drag Listeners
        initComparisonSlider();

        // Setup Download Handler
        setupDownloadButton(photoData);
    }

    function updateBeforeImageWidth() {
        if (comparisonSlider && beforeImg) {
            const containerWidth = comparisonSlider.offsetWidth;
            if (containerWidth > 0) {
                beforeImg.style.width = `${containerWidth}px`;
            }
        }
    }

    function showLoadingState() {
        viewLoading.style.display = 'block';
        viewResult.style.display = 'none';
        viewError.style.display = 'none';
        titleEl.textContent = 'Transforming Your Portrait';
        subtitleEl.textContent = 'Pixora AI is crafting your studio-quality photograph.';
    }

    function showErrorState(msg) {
        clearInterval(progressTimer);
        clearInterval(pollInterval);
        viewLoading.style.display = 'none';
        viewResult.style.display = 'none';
        viewError.style.display = 'block';
        errorMessageEl.textContent = msg;
        titleEl.textContent = 'Generation Interrupted';
        subtitleEl.textContent = 'We were unable to complete your portrait transformation.';
    }

    /**
     * Interactive Before/After Comparison Slider with Mouse + Touch Dragging
     */
    function initComparisonSlider() {
        let isDragging = false;

        function setSliderPosition(xPos) {
            const rect = comparisonSlider.getBoundingClientRect();
            let position = ((xPos - rect.left) / rect.width) * 100;

            // Clamp between 0% and 100%
            if (position < 0) position = 0;
            if (position > 100) position = 100;

            beforeLayer.style.width = `${position}%`;
            sliderHandle.style.left = `${position}%`;
        }

        // Mouse Events
        comparisonSlider.addEventListener('mousedown', (e) => {
            isDragging = true;
            setSliderPosition(e.clientX);
        });

        window.addEventListener('mousemove', (e) => {
            if (!isDragging) return;
            setSliderPosition(e.clientX);
        });

        window.addEventListener('mouseup', () => {
            isDragging = false;
        });

        // Touch Events (Mobile)
        comparisonSlider.addEventListener('touchstart', (e) => {
            isDragging = true;
            if (e.touches.length > 0) {
                setSliderPosition(e.touches[0].clientX);
            }
        }, { passive: true });

        window.addEventListener('touchmove', (e) => {
            if (!isDragging) return;
            if (e.touches.length > 0) {
                setSliderPosition(e.touches[0].clientX);
            }
        }, { passive: true });

        window.addEventListener('touchend', () => {
            isDragging = false;
        });

        // Default to center 50%
        beforeLayer.style.width = '50%';
        sliderHandle.style.left = '50%';
    }

    /**
     * High-res image download handler
     */
    function setupDownloadButton(photoData) {
        btnDownload.addEventListener('click', async () => {
            try {
                btnDownload.disabled = true;
                const originalText = btnDownload.innerHTML;
                btnDownload.innerHTML = '<span class="spinner"></span> <span>Preparing Download...</span>';

                const response = await fetch(photoData.generatedImageUrl);
                const blob = await response.blob();
                const blobUrl = window.URL.createObjectURL(blob);

                const link = document.createElement('a');
                link.href = blobUrl;
                link.download = `pixora-${photoData.photoType ? photoData.photoType.toLowerCase() : 'portrait'}-${photoId}.png`;
                document.body.appendChild(link);
                link.click();
                document.body.removeChild(link);
                window.URL.revokeObjectURL(blobUrl);

                btnDownload.disabled = false;
                btnDownload.innerHTML = originalText;
            } catch (err) {
                console.warn('Direct blob download failed, falling back to direct tab open:', err);
                window.open(photoData.generatedImageUrl, '_blank');
                btnDownload.disabled = false;
                btnDownload.innerHTML = '<span>📥 Download HD Photo</span>';
            }
        });
    }
});
