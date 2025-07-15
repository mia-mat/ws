const caret = document.getElementById('shell-caret');

function updateCaretBlink() {
    if (document.hasFocus()) {
        caret.classList.add('blinking');
    } else {
        caret.classList.remove('blinking');
        caret.style.opacity = '1'; // Ensure caret visible when not blinking
    }
}

updateCaretBlink();

window.addEventListener('focus', updateCaretBlink);
window.addEventListener('blur', updateCaretBlink);