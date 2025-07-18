const cursor = document.querySelector('.custom-cursor');

document.addEventListener('mousemove', (e) => {
    if(isMobile) return;
    cursor.style.left = `${e.clientX}px`;
    cursor.style.top = `${e.clientY}px`;
    cursor.style.opacity = '1';
});

document.addEventListener('mouseleave', () => {
    cursor.style.opacity = '0';
});

document.addEventListener('mouseenter', () => {
    if(isMobile) return;
    cursor.style.opacity = '1';
});

// we can't tell position on page load, so just hide the cursor until move
cursor.style.opacity = '0';