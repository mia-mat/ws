// header scroll effect
const navUl = document.querySelector('nav ul');
navUl.addEventListener('wheel', function(e) {
    if (e.deltaY !== 0) {
        e.preventDefault(); // prevent vertical scroll
        navUl.scrollLeft += e.deltaY; // scroll horizontally
    }
});


// smooth scrolling for navigation links
document.querySelectorAll('nav a, .hero-buttons a').forEach(anchor => {
    anchor.addEventListener('click', function (e) {
        e.preventDefault();
        const targetId = this.getAttribute('href');
        const targetElement = document.querySelector(targetId);

        window.scrollTo({
            top: targetElement.offsetTop - 80,
            behavior: 'smooth'
        });
    });
});