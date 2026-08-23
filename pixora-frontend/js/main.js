import { checkBackendHealth } from './api.js';
import { initNavAuth } from './auth.js';

document.addEventListener('DOMContentLoaded', () => {
    // 1. Initialize Navigation Auth State
    initNavAuth();

    // 2. Initialize Intersection Observer for Scroll Reveal Animations
    initScrollReveal();

    // 3. Initialize Smooth Scrolling for Internal Links
    initSmoothScroll();

    // 4. Check Backend Connectivity
    initHealthCheck();
});

/**
 * Scroll Reveal Animation using IntersectionObserver
 */
function initScrollReveal() {
    const reveals = document.querySelectorAll('.reveal');
    if (!reveals.length) return;

    if ('IntersectionObserver' in window) {
        const observer = new IntersectionObserver((entries, observerInstance) => {
            entries.forEach(entry => {
                if (entry.isIntersecting) {
                    entry.target.classList.add('active');
                    // Once revealed, unobserve to keep DOM performant
                    observerInstance.unobserve(entry.target);
                }
            });
        }, {
            root: null,
            threshold: 0.12,
            rootMargin: '0px 0px -40px 0px'
        });

        reveals.forEach(el => observer.observe(el));
    } else {
        // Fallback for older browsers
        reveals.forEach(el => el.classList.add('active'));
    }
}

/**
 * Smooth Scrolling for Anchor Links (e.g. #how-it-works)
 */
function initSmoothScroll() {
    document.querySelectorAll('a[href^="#"]').forEach(anchor => {
        anchor.addEventListener('click', function (e) {
            const targetId = this.getAttribute('href');
            if (targetId === '#' || targetId === '') return;

            const targetElement = document.querySelector(targetId);
            if (targetElement) {
                e.preventDefault();
                targetElement.scrollIntoView({
                    behavior: 'smooth',
                    block: 'start'
                });
            }
        });
    });
}

/**
 * Backend Connectivity Checker
 */
async function initHealthCheck() {
    const statusBadge = document.getElementById('backend-status');
    if (!statusBadge) return;

    try {
        const data = await checkBackendHealth();
        if (data && data.status === 'ok') {
            statusBadge.innerHTML = '<span class="status-dot"></span> Backend connected ✓';
            statusBadge.className = 'status-badge connected';
        } else {
            statusBadge.innerHTML = '<span class="status-dot"></span> Backend response unexpected';
            statusBadge.className = 'status-badge disconnected';
        }
    } catch (err) {
        statusBadge.innerHTML = '<span class="status-dot"></span> Backend disconnected ✗ (Run Spring Boot on port 8080)';
        statusBadge.className = 'status-badge disconnected';
    }
}
