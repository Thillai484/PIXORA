/**
 * Pixora Firebase Configuration
 * Live Firebase Project: pixora-9223f
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
    apiKey: "AIzaSyBkC-RFa7hbhfHOxKi2JmygYYy9Qe_YNFM",
    authDomain: "pixora-9223f.firebaseapp.com",
    projectId: "pixora-9223f",
    storageBucket: "pixora-9223f.firebasestorage.app",
    messagingSenderId: "353315934816",
    appId: "1:353315934816:web:ca9da51511341aa82fa905",
    measurementId: "G-42ENX4D46X"
};

// Check if valid config is present
export const isFirebaseConfigured = () => {
    return firebaseConfig.apiKey && !firebaseConfig.apiKey.includes('YOUR_FIREBASE_API_KEY');
};

const app = getApps().length === 0 ? initializeApp(firebaseConfig) : getApps()[0];
const auth = getAuth(app);
const googleProvider = new GoogleAuthProvider();
googleProvider.setCustomParameters({ prompt: 'select_account' });

export { app, auth, googleProvider, signInWithPopup, firebaseSignOut, onAuthStateChanged };
