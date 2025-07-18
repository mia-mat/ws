// BUBBLES :3

const particleCount = 40;

const particlesContainer = document.getElementById('particles');

for (let i = 0; i < particleCount; i++) {
    const particle = document.createElement('div');

    const sizeType = Math.random();
    if (sizeType < 0.3) {
        particle.classList.add('particle', 'large');
    } else if (sizeType < 0.6) {
        particle.classList.add('particle', 'medium');
    } else {
        particle.classList.add('particle', 'small');
    }

    // add pulse animation to some particles
    if (Math.random() > 0.7) {
        particle.classList.add('pulse');
    }

    const posX = Math.random() * 100;
    const posY = Math.random() * 100;
    particle.style.left = `${posX}%`;
    particle.style.top = `${posY}%`;

    particle.style.opacity = Math.random() * 0.6 + 0.1;

    const animationDelay = Math.random() * 5;
    const animationDuration = Math.random() * 15 + 10;
    particle.style.animationDelay = `${animationDelay}s`;
    particle.style.animationDuration = `${animationDuration}s`;

    particlesContainer.appendChild(particle);
}