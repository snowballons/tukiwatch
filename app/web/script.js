/* ═══════════════════════════════════════════════════════════
   StreamWatch — script.js
   Handles: nav scroll, mobile menu, FAQ, scroll animation,
            back-to-top, download toast, notify form, GitHub stars
═══════════════════════════════════════════════════════════ */

document.addEventListener('DOMContentLoaded', () => {

    /* ──────────────────────────────────────────
       1. SMOOTH SCROLL for anchor links
    ────────────────────────────────────────── */
    document.querySelectorAll('a[href^="#"]').forEach(anchor => {
        anchor.addEventListener('click', function (e) {
            const href = this.getAttribute('href');
            if (href === '#') return;
            const target = document.querySelector(href);
            if (target) {
                e.preventDefault();
                const headerH = document.getElementById('header')?.offsetHeight || 64;
                const top = target.getBoundingClientRect().top + window.scrollY - headerH - 16;
                window.scrollTo({ top, behavior: 'smooth' });
                // Close mobile menu if open
                closeMobileMenu();
            }
        });
    });


    /* ──────────────────────────────────────────
       2. HEADER — scroll shadow
    ────────────────────────────────────────── */
    const header = document.getElementById('header');
    const onScroll = () => {
        if (window.scrollY > 12) {
            header?.classList.add('scrolled');
        } else {
            header?.classList.remove('scrolled');
        }
    };
    window.addEventListener('scroll', onScroll, { passive: true });


    /* ──────────────────────────────────────────
       3. MOBILE MENU toggle
    ────────────────────────────────────────── */
    const menuToggle = document.getElementById('menu-toggle');
    const mobileMenu = document.getElementById('mobile-menu');

    function openMobileMenu() {
        mobileMenu?.removeAttribute('hidden');
        menuToggle?.setAttribute('aria-expanded', 'true');
        document.body.style.overflow = 'hidden';
    }

    function closeMobileMenu() {
        mobileMenu?.setAttribute('hidden', '');
        menuToggle?.setAttribute('aria-expanded', 'false');
        document.body.style.overflow = '';
    }

    menuToggle?.addEventListener('click', () => {
        const isOpen = menuToggle.getAttribute('aria-expanded') === 'true';
        isOpen ? closeMobileMenu() : openMobileMenu();
    });

    // Close on Escape
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') closeMobileMenu();
    });


    /* ──────────────────────────────────────────
       4. SCROLL FADE-IN (IntersectionObserver)
    ────────────────────────────────────────── */
    const fadeEls = document.querySelectorAll('.fade-in');
    if ('IntersectionObserver' in window && fadeEls.length) {
        const io = new IntersectionObserver((entries, observer) => {
            entries.forEach(entry => {
                if (entry.isIntersecting) {
                    // Stagger child cards if present
                    const cards = entry.target.querySelectorAll(
                        '.feature-card, .value-card, .screenshot-item, .faq-item'
                    );
                    cards.forEach((card, i) => {
                        card.style.transitionDelay = `${i * 60}ms`;
                    });
                    entry.target.classList.add('fade-in-visible');
                    observer.unobserve(entry.target);
                }
            });
        }, { threshold: 0.08 });

        fadeEls.forEach(el => io.observe(el));
    } else {
        // Fallback: just show everything
        fadeEls.forEach(el => el.classList.add('fade-in-visible'));
    }


    /* ──────────────────────────────────────────
       5. FAQ ACCORDION
    ────────────────────────────────────────── */
    document.querySelectorAll('.faq-question').forEach(btn => {
        btn.addEventListener('click', () => {
            const isOpen  = btn.getAttribute('aria-expanded') === 'true';
            const answer  = btn.nextElementSibling;

            // Close all
            document.querySelectorAll('.faq-question').forEach(b => {
                b.setAttribute('aria-expanded', 'false');
                b.nextElementSibling?.classList.remove('open');
            });

            // Toggle clicked
            if (!isOpen) {
                btn.setAttribute('aria-expanded', 'true');
                answer?.classList.add('open');
            }
        });
    });


    /* ──────────────────────────────────────────
       6. BACK TO TOP button
    ────────────────────────────────────────── */
    const backBtn = document.getElementById('back-to-top');
    const toggleBackBtn = () => {
        if (window.scrollY > 400) {
            backBtn?.classList.add('visible');
        } else {
            backBtn?.classList.remove('visible');
        }
    };
    window.addEventListener('scroll', toggleBackBtn, { passive: true });

    backBtn?.addEventListener('click', () => {
        window.scrollTo({ top: 0, behavior: 'smooth' });
    });


    /* ──────────────────────────────────────────
       7. DOWNLOAD TOAST
    ────────────────────────────────────────── */
    const toast = document.getElementById('toast');

    function showToast() {
        if (!toast) return;
        toast.classList.add('show');
        setTimeout(() => toast.classList.remove('show'), 3500);
    }

    ['apk-btn', 'apk-btn-2'].forEach(id => {
        document.getElementById(id)?.addEventListener('click', () => {
            // Small delay so browser starts download first
            setTimeout(showToast, 400);
        });
    });


    /* ──────────────────────────────────────────
       8. iOS NOTIFY FORM (client-side only)
         Replace with a real backend / Mailchimp
         endpoint as needed.
    ────────────────────────────────────────── */
    const notifyForm = document.getElementById('notify-form');
    const notifyConf = document.getElementById('notify-confirmation');

    notifyForm?.addEventListener('submit', (e) => {
        e.preventDefault();
        const emailInput = document.getElementById('notify-email');
        const email      = emailInput?.value?.trim();
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

        if (!email || !emailRegex.test(email)) {
            if (notifyConf) {
                notifyConf.style.color = '#FF6B6B';
                notifyConf.textContent = '→ Please enter a valid email address.';
            }
            emailInput?.focus();
            return;
        }

        // Success UI (wire up to real service here)
        if (notifyConf) {
            notifyConf.style.color = '';
            notifyConf.textContent = `→ Got it! We'll notify ${email} when iOS launches.`;
        }
        if (emailInput) emailInput.value = '';

        // Example fetch to your backend:
        // fetch('/api/notify', {
        //     method: 'POST',
        //     headers: { 'Content-Type': 'application/json' },
        //     body: JSON.stringify({ email })
        // }).catch(console.error);
    });


    /* ──────────────────────────────────────────
       9. GITHUB STARS (live fetch)
    ────────────────────────────────────────── */
    const starsEl = document.getElementById('gh-stars');
    if (starsEl) {
        fetch('https://api.github.com/repos/snowballons/streamwatch-api', {
            headers: { Accept: 'application/vnd.github.v3+json' }
        })
        .then(r => r.ok ? r.json() : null)
        .then(data => {
            if (data?.stargazers_count != null) {
                const n = data.stargazers_count;
                starsEl.textContent = n >= 1000
                    ? (n / 1000).toFixed(1) + 'k'
                    : n;
            }
        })
        .catch(() => {
            // Silently fail — the "—" placeholder stays
        });
    }

});
