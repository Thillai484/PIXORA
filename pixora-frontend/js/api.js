/**
 * Pixora API Client
 * Centralized API configuration and network helper.
 * Switching BASE_URL here will update the backend URL across the entire frontend.
 */

const isLocalhost = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1';

// In production, connect to your live Render backend URL
export const BASE_URL = isLocalhost 
    ? 'http://localhost:8080/api' 
    : 'https://pixora-vo4x.onrender.com/api';

/**
 * Health check helper to verify backend connectivity
 */
export async function checkBackendHealth() {
    try {
        const response = await fetch(`${BASE_URL}/health`, {
            method: 'GET',
            headers: {
                'Accept': 'application/json'
            }
        });
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        return await response.json();
    } catch (error) {
        console.error('Backend health check failed:', error);
        throw error;
    }
}

/**
 * Generic authenticated fetch wrapper
 */
export async function apiFetch(endpoint, options = {}) {
    const url = endpoint.startsWith('http') ? endpoint : `${BASE_URL}${endpoint}`;
    const token = sessionStorage.getItem('pixora_token');

    const headers = {
        'Accept': 'application/json',
        'Content-Type': 'application/json',
        ...(options.headers || {})
    };

    if (token) {
        headers['Authorization'] = `Bearer ${token}`;
    }

    const response = await fetch(url, {
        ...options,
        headers
    });

    if (response.status === 401) {
        sessionStorage.removeItem('pixora_token');
        sessionStorage.removeItem('pixora_user');
        // Redirect to login if on protected page
        if (!window.location.pathname.endsWith('login.html') && !window.location.pathname.endsWith('index.html')) {
            window.location.href = 'login.html';
        }
    }

    return response;
}

/**
 * Upload Photo API
 */
export async function uploadPhoto(file) {
    const formData = new FormData();
    formData.append('file', file);

    const token = sessionStorage.getItem('pixora_token');
    const headers = {
        'Accept': 'application/json'
    };
    if (token) {
        headers['Authorization'] = `Bearer ${token}`;
    }

    const response = await fetch(`${BASE_URL}/photos/upload`, {
        method: 'POST',
        headers,
        body: formData
    });

    const data = await response.json();
    if (!response.ok) {
        throw new Error(data.message || 'Upload failed');
    }
    return data;
}

/**
 * Customize Photo API
 */
export async function customizePhoto(customizationData) {
    const response = await apiFetch('/photos/customize', {
        method: 'POST',
        body: JSON.stringify(customizationData)
    });

    const data = await response.json();
    if (!response.ok) {
        throw new Error(data.message || 'Customization failed');
    }
    return data;
}

/**
 * Start AI Generation API
 */
export async function startGeneration(photoId) {
    const response = await apiFetch(`/photos/${photoId}/generate`, {
        method: 'POST'
    });

    const data = await response.json();
    if (!response.ok && response.status !== 202) {
        throw new Error(data.message || 'Failed to start generation');
    }
    return data;
}

/**
 * Start Photo Pack (Batch Generation) API
 */
export async function generatePhotoPack(photoId, packType) {
    const response = await apiFetch(`/photos/${photoId}/pack`, {
        method: 'POST',
        body: JSON.stringify({ packType })
    });

    const data = await response.json();
    if (!response.ok && response.status !== 202) {
        throw new Error(data.message || 'Failed to start photo pack generation');
    }
    return data;
}

/**
 * Get Live Generation Status API
 */
export async function getPhotoStatus(photoId) {
    const response = await apiFetch(`/photos/${photoId}/status`);
    const data = await response.json();
    if (!response.ok) {
        throw new Error(data.message || 'Failed to fetch status');
    }
    return data;
}

/**
 * Get Photo Details API
 */
export async function getPhoto(photoId) {
    const response = await apiFetch(`/photos/${photoId}`);
    const data = await response.json();
    if (!response.ok) {
        throw new Error(data.message || 'Failed to fetch photo');
    }
    return data;
}

/**
 * Get all photos belonging to the authenticated user
 */
export async function getMyPhotos() {
    const response = await apiFetch('/photos');
    const data = await response.json();
    if (!response.ok) {
        throw new Error(data.message || 'Failed to fetch photos');
    }
    return data;
}

/**
 * Delete a photo by ID
 */
export async function deletePhoto(photoId) {
    const response = await apiFetch(`/photos/${photoId}`, {
        method: 'DELETE'
    });
    const data = await response.json();
    if (!response.ok) {
        throw new Error(data.message || 'Failed to delete photo');
    }
    return data;
}
