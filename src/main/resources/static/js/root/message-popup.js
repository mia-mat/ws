const contactForm = document.querySelector('#contact form');
const thankYouPopup = document.getElementById('thankYouPopup');
const closePopupBtn = document.getElementById('closePopup');

contactForm.addEventListener('submit', function(e) {
    e.preventDefault();

    const formData = new FormData(contactForm);
    fetch(contactForm.action, {
        method: 'POST',
        body: formData,
    })
        .then(response => {
            if (!response.ok) {
                throw new Error('Network response was not OK');
            }

            thankYouPopup.classList.add('active');

            contactForm.reset();
        })
        .catch(error => {
            alert('There was a problem sending your message. Please try again.');
            console.error('Error:', error);
        });

});

// close popup when clicking the close button
closePopupBtn.addEventListener('click', function() {
    thankYouPopup.classList.remove('active');
});

// close popup when clicking outside the content
thankYouPopup.addEventListener('click', function(e) {
    if (e.target === thankYouPopup) {
        thankYouPopup.classList.remove('active');
    }
});

// close popup when pressing Escape key
document.addEventListener('keydown', function(e) {
    if (e.key === 'Escape' && thankYouPopup.classList.contains('active')) {
        thankYouPopup.classList.remove('active');
    }
});