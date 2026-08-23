/**
 * Pixora Firebase Configuration
 * Replace the placeholder values below with your Firebase Project configuration
 * from: https://console.firebase.google.com/ -> Project Settings -> General -> Your apps
 */
import { initializeApp, getApps } from 'https://www.gstatic.com/firebasejs/10.13.0/firebase-app.js';
import { 
    getAuth, 
    GoogleAuthProvider, 
    signInWithPopup, 
    signOut as firebaseSignOut,
    onAuthStateChanged 
} from 'https://www.gstatic.com/firebasejs/10.13.0/firebase-auth.js';

export const firebaseConfig = {
    apiKey: "YOUR_FIREBASE_API_KEY_HERE",
    authDomain: "pixora-app.firebaseapp.com",
    projectId: "pixora-app",
    storageBucket: "pixora-app.appspot.com",
    messagingSenderId: "123456789012",
    appId: "1:123456789012:web:abcdef1234567890"
};

// Check if valid config is present
export const isFirebaseConfigured = () => {
    return firebaseConfig.apiKey && !firebaseConfig.apiKey.includes('YOUR_FIREBASE_API_KEY');
};

let app;
let auth;
let googleProvider;

if (isFirebaseConfigured()) {
    app = getApps().length === 0 ? initializeApp(firebaseConfig) : getApps()[0];
    auth = getAuth(app);
    googleProvider = new GoogleAuthProvider();
    googleProvider.setCustomParameters({ prompt: 'select_account' });
} else {
    console.warn('Firebase configuration contains placeholder keys. Real Google popup requires valid Firebase credentials.');
}

export { app, auth, googleProvider, signInWithPopup, firebaseSignOut, onAuthStateChanged };
