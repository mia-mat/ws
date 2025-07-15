const shellOutDiv = document.getElementById("shell-out");

const keyHandlers = {
    Backspace: () => {
        shellOutDiv.textContent = shellOutDiv.textContent.slice(0, -1);
    },
    Enter: () => {
        // exec command
        startNewCommandInput();
    },
    Tab: () => {
        shellOutDiv.append("\t")
    },
};

window.addEventListener('keydown', function (e) {
    e.preventDefault();

    if (keyHandlers[e.key]) {
        keyHandlers[e.key]();
    } else if (e.key.length === 1 && !e.metaKey && !e.ctrlKey) {
        shellOutDiv.append(e.key);
    }

});

function startNewCommandInput() {
    shellOutDiv.append("\n[user@mia.ws ~]# ")
}