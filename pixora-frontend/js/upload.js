/**
 * Pixora Upload Controller
 * Handles drag-and-drop, client-side validation, instant preview, and API upload
 */
import { uploadPhoto } from './api.js';

document.addEventListener('DOMContentLoaded', () => {
    const dropzone = document.getElementById('dropzone');
    const fileInput = document.getElementById('file-input');
    const browseBtn = document.getElementById('browse-btn');
    const previewContainer = document.getElementById('preview-container');
    const previewImage = document.getElementById('preview-image');
    const metaName = document.getElementById('meta-name');
    const metaSize = document.getElementById('meta-size');
    const metaDim = document.getElementById('meta-dim');
    const changePhotoBtn = document.getElementById('change-photo-btn');
    const continueBtn = document.getElementById('continue-btn');
    const uploadingState = document.getElementById('uploading-state');
    const alertBox = document.getElementById('upload-alert');

    let currentFile = null;

    const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/jpg', 'image/webp'];
    const MAX_SIZE_BYTES = 10 * 1024 * 1024; // 10MB

    // Browse Button Click
    browseBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        fileInput.click();
    });

    // Dropzone Click
    dropzone.addEventListener('click', () => {
        fileInput.click();
    });

    // Drag & Drop Events
    ['dragenter', 'dragover'].forEach(eventName => {
        dropzone.addEventListener(eventName, (e) => {
            e.preventDefault();
            e.stopPropagation();
            dropzone.classList.add('dragover');
        }, false);
    });

    ['dragleave', 'drop'].forEach(eventName => {
        dropzone.addEventListener(eventName, (e) => {
            e.preventDefault();
            e.stopPropagation();
            dropzone.classList.remove('dragover');
        }, false);
    });

    dropzone.addEventListener('drop', (e) => {
        const dt = e.dataTransfer;
        const files = dt.files;
        if (files && files.length > 0) {
            handleFileSelected(files[0]);
        }
    });

    // File Input Change
    fileInput.addEventListener('change', (e) => {
        if (e.target.files && e.target.files.length > 0) {
            handleFileSelected(e.target.files[0]);
        }
    });

    // Change Photo / Reset
    changePhotoBtn.addEventListener('click', () => {
        resetUpload();
    });

    // Handle File Selection & Validation
    function handleFileSelected(file) {
        hideAlert();

        if (!file) {
            showAlert('Please upload an image.');
            return;
        }

        // Validate Type
        if (!ALLOWED_TYPES.includes(file.type.toLowerCase())) {
            showAlert('Unsupported file format. Please upload a JPG or PNG.');
            return;
        }

        // Validate Size
        if (file.size > MAX_SIZE_BYTES) {
            showAlert('Image size is too large. Maximum size is 10MB.');
            return;
        }

        currentFile = file;

        // Render Instant Preview
        const reader = new FileReader();
        reader.onload = (e) => {
            previewImage.src = e.target.result;

            // Compute dimensions
            const img = new Image();
            img.onload = () => {
                metaDim.textContent = `${img.width} × ${img.height} px`;
            };
            img.src = e.target.result;

            metaName.textContent = file.name;
            metaSize.textContent = formatBytes(file.size);

            dropzone.style.display = 'none';
            previewContainer.style.display = 'flex';
        };

        reader.onerror = () => {
            showAlert('Failed to read image file.');
        };

        reader.readAsDataURL(file);
    }

    // Submit Upload
    continueBtn.addEventListener('click', async () => {
        if (!currentFile) {
            showAlert('Please select a photo first.');
            return;
        }

        try {
            hideAlert();
            previewContainer.style.display = 'none';
            uploadingState.style.display = 'block';

            // Send to Backend POST /api/photos/upload
            const response = await uploadPhoto(currentFile);

            if (response.success && response.photoId) {
                // Store active photo details in session
                sessionStorage.setItem('pixora_current_photo_id', response.photoId);
                sessionStorage.setItem('pixora_current_photo_url', response.originalImageUrl);

                // Redirect to customize step
                window.location.href = `customize.html?photoId=${response.photoId}`;
            } else {
                throw new Error(response.message || 'Upload failed');
            }
        } catch (error) {
            console.error('Upload error:', error);
            uploadingState.style.display = 'none';
            previewContainer.style.display = 'flex';
            showAlert(error.message || 'Failed to upload image. Please try again.');
        }
    });

    function resetUpload() {
        currentFile = null;
        fileInput.value = '';
        previewImage.src = '';
        dropzone.style.display = 'block';
        previewContainer.style.display = 'none';
        uploadingState.style.display = 'none';
        hideAlert();
    }

    function showAlert(msg) {
        alertBox.textContent = msg;
        alertBox.style.display = 'block';
    }

    function hideAlert() {
        alertBox.style.display = 'none';
        alertBox.textContent = '';
    }

    function formatBytes(bytes) {
        if (bytes === 0) return '0 Bytes';
        const k = 1024;
        const sizes = ['Bytes', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
    }
});
