// script.js for subtle animations and interactions

document.addEventListener('DOMContentLoaded', () => {
  // Smooth scrolling for navigation links
  document.querySelectorAll('nav ul li a, .footer-links a').forEach((anchor) => {
    anchor.addEventListener('click', function (e) {
      const href = this.getAttribute('href');
      if (href.startsWith('#')) {
        e.preventDefault();
        const targetElement = document.querySelector(href);
        if (targetElement) {
          targetElement.scrollIntoView({
            behavior: 'smooth',
          });
        }
      }
    });
  });

  // Simple scroll-based animation (fade-in for sections)
  const observerOptions = {
    root: null,
    rootMargin: '0px',
    threshold: 0.1,
  };

  const observer = new IntersectionObserver((entries, observer) => {
    entries.forEach((entry) => {
      if (entry.isIntersecting) {
        entry.target.classList.add('fade-in-visible');
        observer.unobserve(entry.target);
      }
    });
  }, observerOptions);

  document.querySelectorAll('section, footer').forEach((section) => {
    section.classList.add('fade-in'); // Add initial class for styling
    observer.observe(section);
  });

  // Auto-Update Download Links from GitHub Releases
  async function fetchLatestRelease() {
    try {
      const response = await fetch(
        'https://api.github.com/repos/snowballons/streamwatch-api/releases/latest'
      );
      if (!response.ok) throw new Error('Network response was not ok');
      const data = await response.json();

      const version = data.tag_name;
      const apkAsset = data.assets.find((asset) => asset.name.endsWith('.apk'));
      const downloadUrl = apkAsset ? apkAsset.browser_download_url : data.html_url;

      // Update buttons
      const heroBtn = document.getElementById('hero-download-btn');
      const footerBtn = document.getElementById('footer-download-btn');

      if (heroBtn) {
        heroBtn.href = downloadUrl;
        heroBtn.querySelector('.version-text').textContent = `(${version})`;
        heroBtn.setAttribute('aria-label', `Download StreamWatch APK for Android - ${version}`);
      }
      if (footerBtn) {
        footerBtn.href = downloadUrl;
        footerBtn.querySelector('.version-text').textContent = `(${version})`;
        footerBtn.setAttribute('aria-label', `Download StreamWatch Android APK - ${version}`);
      }
    } catch (error) {
      console.error('Error fetching latest release:', error);
      // Fallback is already handled by HTML having the /releases/latest link
    }
  }

  fetchLatestRelease();
});
