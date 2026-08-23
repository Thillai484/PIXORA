/**
 * Pixora Authentication Manager
 * Handles Google Sign-In, Session Management, and Nav UI State
 */
import { 
    auth, 
    googleProvider, 
    signInWithPopup, 
    firebaseSignOut, 
    isFirebaseConfigured 
} from './firebase-config.js';
import { BASE_URL, apiFetch } from './api.js';

const TOKEN_KEY = 'pixora_token';
const USER_KEY = 'pixora_user';

/**
 * Perform Google Sign-In
 */
export async function signInWithGoogle() {
    try {
        let idToken = null;
        let userProfile = null;

        if (isFirebaseConfigured() && auth && googleProvider) {
            // Real Firebase Google Sign-In Popup
            const result = await signInWithPopup(auth, googleProvider);
            idToken = await result.user.getIdToken();
            userProfile = {
                uid: result.user.uid,
                email: result.user.email,
                name: result.user.displayName,
                avatarUrl: result.user.photoURL
            };
        } else {
            // Development / Demo Fallback Mode
            console.info('Using development demo authentication mode');
            const demoName = prompt('Enter a demo user name (or press OK for default):', 'Demo User') || 'Demo User';
            const slug = demoName.toLowerCase().replace(/[^a-z0-9]/g, '');
            idToken = `test-token-${slug || 'dev'}`;
            userProfile = {
                uid: `test-uid-${slug || 'dev'}`,
                email: `${slug || 'dev'}@pixora.app`,
                name: demoName,
                avatarUrl: `https://api.dicebear.com/7.x/bottts/svg?seed=${slug || 'pixora'}`
            };
        }

        // Sync with Spring Boot backend: POST /api/auth/google
        const response = await fetch(`${BASE_URL}/auth/google`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${idToken}`
            },
            body: JSON.stringify({
                idToken: idToken,
                email: userProfile.email,
                name: userProfile.name,
                avatarUrl: userProfile.avatarUrl
            })
        });

        if (!response.ok) {
            const errData = await response.json().catch(() => ({}));
            throw new Error(errData.message || `Backend auth sync failed with status ${response.status}`);
        }

        const backendUser = await response.json();

        // Save session in sessionStorage
        sessionStorage.setItem(TOKEN_KEY, idToken);
        sessionStorage.setItem(USER_KEY, JSON.stringify(backendUser));

        return backendUser;
    } catch (error) {
        console.error('Google sign-in error:', error);
        throw error;
    }
}

/**
 * Sign out user
 */
export async function signOutUser() {
    try {
        if (isFirebaseConfigured() && auth) {
            await firebaseSignOut(auth);
        }
    } catch (err) {
        console.warn('Firebase signout error:', err);
    } finally {
        sessionStorage.removeItem(TOKEN_KEY);
        sessionStorage.removeItem(USER_KEY);
        window.location.href = 'index.html';
    }
}

/**
 * Get current authenticated user details from session
 */
export function getCurrentUser() {
    try {
        const userStr = sessionStorage.getItem(USER_KEY);
        return userStr ? JSON.parse(userStr) : null;
    } catch (e) {
        return null;
    }
}

/**
 * Get current auth token
 */
export function getAuthToken() {
    return sessionStorage.getItem(TOKEN_KEY);
}

/**
 * Check if user is currently signed in
 */
export function isAuthenticated() {
    return !!getAuthToken() && !!getCurrentUser();
}

/**
 * Protect routes: redirects to login.html if not authenticated
 */
export function requireAuth() {
    if (!isAuthenticated()) {
        sessionStorage.setItem('pixora_redirect', window.location.href);
        window.location.href = 'login.html';
        return false;
    }
    return true;
}

/**
 * Update navigation elements dynamically based on login status
 */
export function initNavAuth() {
    const navLinks = document.querySelector('.nav-links');
    if (!navLinks) return;

    const user = getCurrentUser();

    if (user) {
        // Find existing sign in button or user menu item
        const existingAuthItem = navLinks.querySelector('.nav-auth-item') || navLinks.lastElementChild;
        if (existingAuthItem) {
            const avatar = user.avatarUrl 
                ? `<img src="${user.avatarUrl}" alt="${user.name || 'User'}" class="nav-avatar">` 
                : `<span class="nav-avatar-placeholder">${(user.name || 'U').charAt(0).toUpperCase()}</span>`;

            existingAuthItem.outerHTML = `
                <li class="nav-auth-item user-profile-menu">
                    <div class="user-pill">
                        ${avatar}
                        <span class="user-name">${user.name || user.email || 'User'}</span>
                        <button id="nav-btn-signout" class="btn btn-secondary btn-nav-signout" title="Sign Out">
                            Sign Out
                        </button>
                    </div>
                </li>
            `;

            document.getElementById('nav-btn-signout')?.addEventListener('click', (e) => {
                e.preventDefault();
                signOutUser();
            });
        }
    }
}
