// TukiWatch website — interactions and dynamic content

document.addEventListener('DOMContentLoaded', () => {
  // Smooth scrolling for anchor links
  document.querySelectorAll('a[href^="#"]').forEach((anchor) => {
    anchor.addEventListener('click', function (e) {
      const href = this.getAttribute('href');
      if (href === '#') return;
      e.preventDefault();
      const target = document.querySelector(href);
      if (target) {
        target.scrollIntoView({ behavior: 'smooth' });
        // Update URL without jump
        history.pushState(null, '', href);
      }
    });
  });

  // Fetch latest APK version and update download buttons
  async function fetchLatestRelease() {
    try {
      const response = await fetch('https://downloads.snowballons.com/version.json', {
        headers: { 'Cache-Control': 'no-cache' },
      });
      if (!response.ok) throw new Error('Version fetch failed');
      const data = await response.json();

      const version = `v${data.version}`;
      const downloadUrl = data.apkUrl;

      // Update all download buttons
      document
        .querySelectorAll('#hero-download-btn, #footer-download-btn, #nav-download')
        .forEach((btn) => {
          if (btn) {
            btn.href = downloadUrl;
            const versionSpan = btn.querySelector('.btn__version');
            if (versionSpan) versionSpan.textContent = `(${version})`;
          }
        });
    } catch (error) {
      console.warn('Could not fetch latest version:', error);
      // Buttons keep their default hrefs from HTML
    }
  }

  fetchLatestRelease();

  // Intersection observer for fade-in sections
  const observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add('fade-in-visible');
          observer.unobserve(entry.target);
        }
      });
    },
    { threshold: 0.1, rootMargin: '0px 0px -40px 0px' }
  );

  document.querySelectorAll('.feature, .step, .screens__item').forEach((el) => {
    el.classList.add('fade-in');
    observer.observe(el);
  });
});

// Fade-in base styles (injected here to avoid separate file)
const style = document.createElement('style');
style.textContent = `
  .fade-in {
    opacity: 0;
    transform: translateY(16px);
    transition: opacity 0.5s var(--ease-out), transform 0.5s var(--ease-out);
  }
  .fade-in-visible {
    opacity: 1;
    transform: translateY(0);
  }
  @media (prefers-reduced-motion: reduce) {
    .fade-in {
      opacity: 1;
      transform: none;
      transition: none;
    }
  }
`;
document.head.appendChild(style);
