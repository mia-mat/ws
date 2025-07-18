const linkSelectors = [
    'nav a',
    'cta-button',
    'social-link',
    'contact-link',
    'footer-links a',
];

// Combine all selectors into one string
document.querySelectorAll(linkSelectors.join(',')).forEach(link => {
    link.addEventListener('click', () => {
        link.blur();  // unselect after click
    });
});