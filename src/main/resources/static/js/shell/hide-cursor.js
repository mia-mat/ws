function updateCursor() {
    if (document.hasFocus()) {
        document.documentElement.style.cursor = "none";
    } else {
        document.documentElement.style.cursor = "auto";
    }
}

window.addEventListener("focus", updateCursor);
window.addEventListener("blur", updateCursor);

updateCursor();