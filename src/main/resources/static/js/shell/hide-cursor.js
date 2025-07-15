function updateCursor() {
    if (document.hasFocus()) {
        document.documentElement.style.cursor = "none";
    } else {
        document.documentElement.style.cursor = "auto"; // or "" to reset
    }
}

window.addEventListener("focus", updateCursor);
window.addEventListener("blur", updateCursor);

updateCursor();