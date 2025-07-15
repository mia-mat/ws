// fuck js

const STATIC_INPUT_COMMAND_START = () => {
    const homeDir = `/home/${shellState.username}`;
    let dir;

    if (shellState.currentDirectory === homeDir) {
        dir = "~";
    } else if (shellState.currentDirectory.startsWith(homeDir + "/")) {
        // show only the last segment after ~/
        dir = shellState.currentDirectory.substring(homeDir.length + 1).split("/").pop();
    } else {
        // outside ~, show last directory in full path
        dir = shellState.currentDirectory.split("/").pop() || "/";
    }

    return `[${shellState.username}@mia.ws ${dir}]# `;
};
const STATIC_INPUT_CONTINUED = "> "

const endsWithUnescapedBackslash = str => /(?<!\\)(?:\\\\)*\\$/.test(str);

const linesContainer = document.getElementById("shell");

const caretElement = document.getElementById("shell-caret");

let shellState = {};

let caretPosition = 0; // from left
// TODO (Low Priority) >> Selection of a sequence of text through shift+arrows

let currentLineElement;
let currentStaticLineElement;
let currentLineInputElement;

// appended to the top
let commandHistory = []; // technically this should be dependent on context, but that's not too important here.
let inputHistoryPosition = 0; // index 0 represents the currently typed command

let commandBuffer = ""; // pushes to this if end of text contains an un-escaped \, instead of executing.
let continuing = 0; // how many lines has the user been continuing the command for: used to rectify command history

const keyHandlers = {

    Backspace: () => {
        if (caretPosition <= 0) return;
        let text = currentLineInputElement.textContent;

        // remove char before pos
        const before = text.slice(0, caretPosition - 1);
        const after = text.slice(caretPosition);
        currentLineInputElement.textContent = before + after;

        caretPosition--;
        updateCaretVisualPosition();
    },
    Enter: async () => {
        if (commandHistory[0] !== "") {
            commandHistory.unshift("");
        }
        inputHistoryPosition = 0;
        caretPosition = 0;

        if (endsWithUnescapedBackslash(currentLineInputElement.textContent)) {
            commandBuffer += currentLineInputElement.textContent.slice(0, -1);
            newLine(STATIC_INPUT_CONTINUED);
            continuing++;
            return;
        }

        // exec command
        await execCurrentLine();
        newLine(STATIC_INPUT_COMMAND_START());
    },
    ArrowUp: () => {
        if (commandHistory.length <= inputHistoryPosition + 1) return;
        inputHistoryPosition++;
        currentLineInputElement.textContent = commandHistory[inputHistoryPosition];
        caretPosition = currentLineInputElement.textContent.length;
        updateCaretVisualPosition();
    },
    ArrowDown: () => {
        if (inputHistoryPosition <= 0) return;
        inputHistoryPosition--;
        currentLineInputElement.textContent = commandHistory[inputHistoryPosition];
        caretPosition = currentLineInputElement.textContent.length;
        updateCaretVisualPosition();
    },
    ArrowLeft: () => {
        if (caretPosition <= 0) return;
        caretPosition--;
        updateCaretVisualPosition();
    },
    ArrowRight: () => {
        if (caretPosition >= currentLineInputElement.textContent.length) {
            caretPosition = currentLineInputElement.textContent.length;
            return;
        }
        caretPosition++;
        updateCaretVisualPosition();
    },
    Tab: () => {
        // TODO >> display possibilities
    },
};

const ctrlKeyHandlers = {
    c: () => {
        appendInputToCurrentLine("^", caretPosition);
        appendInputToCurrentLine("C", caretPosition);

        commandHistory.unshift("");
        inputHistoryPosition = 0;
        caretPosition = 0;

        newLine(STATIC_INPUT_COMMAND_START());

        // remove 1st element in position history as we don't want to save the Ctrl-C'd text
        commandHistory.splice(1, 1);
    }
}

window.addEventListener('keydown', function (e) {
    e.preventDefault();
    if (!shellState.allowingInput) return;

    if (e.ctrlKey) {
        if (ctrlKeyHandlers[e.key]) ctrlKeyHandlers[e.key]();
        return;
    }

    if (keyHandlers[e.key]) {
        keyHandlers[e.key]();
    } else if (e.key.length === 1 && !e.metaKey && !e.ctrlKey) {
        appendInputToCurrentLine(e.key, caretPosition);
    }

});


function newLine(staticContent) {
    if (!shellState.allowingInput) return;

    currentLineElement = document.createElement('div')
    currentLineElement.classList.add("shell-line")
    linesContainer.appendChild(currentLineElement);

    currentStaticLineElement = document.createElement('div');
    currentStaticLineElement.classList.add("shell-line-static")
    currentLineElement.appendChild(currentStaticLineElement);

    currentStaticLineElement.textContent = staticContent;

    currentLineInputElement = document.createElement('div');
    currentLineInputElement.classList.add("shell-line-input")
    currentLineElement.appendChild(currentLineInputElement);

    currentLineElement.appendChild(caretElement);

    updateCaretVisualPosition();
    window.scrollTo(0, document.body.scrollHeight); // scroll to bottom
}

function appendInputToCurrentLine(textToAppend, index = 0) {
    const line = getLine();
    if (textToAppend === " " && line >= 1) {
        const lineWidth = currentLineElement.getBoundingClientRect().width;
        const charsPerLine = Math.floor(lineWidth / getCharWidth());

        const currentLineText = (currentStaticLineElement.textContent + currentLineInputElement.textContent).slice(charsPerLine * line)

        if (currentLineText === "") {
            return; // prevent cursor jump
        }
    }

    caretPosition++;
    appendToCurrentLine(textToAppend, index);


    // If user changes a command in history, just take them back to position 0, as we don't want to edit history.
    // This is slightly different from what tools like cmd.exe do (wherein they store a buffer of edits until executed),
    // but it's good enough here.
    inputHistoryPosition = 0;
    commandHistory[0] = currentLineInputElement.textContent;
}

function appendToCurrentLine(textToAppend, index = 0) {
    let currentText = currentLineInputElement.textContent;
    let newText = currentText.slice(0, index) + textToAppend + currentText.slice(index);

    // replace leading spaces with nbsp's because of wacky browser stuff and not rendering them regularly
    newText = newText.replace(/^ +/, match => "\u00A0".repeat(match.length));

    currentLineInputElement.textContent = newText;

    updateCaretVisualPosition();
    window.scrollTo(0, document.body.scrollHeight); // scroll to bottom
}

async function execCurrentLine() {
    commandBuffer += currentLineInputElement.textContent;

    // replace history with command buffer
    if (continuing !== 0) {
        commandHistory.splice(1, continuing, commandBuffer);
        continuing = 0;
    }

    await fetch('/shell/execute', {
        method: 'POST',
        headers: {
            'Content-Type': 'text/plain',
        },
        body: commandBuffer,
        credentials: "include"
    })
        .then(response => {
            if (!response.ok) throw new Error(`HTTP error ${response.status}`);
            return response.json();
        })
        .then(result => {
            if(result.grantedRootAccess) {
                window.location.href = "/"; // redirect to root
                return;
            }

            result.lines.forEach(line => {
                newLine("");
                appendToCurrentLine(line);
            })

            shellState = result.state;
            updateCaretVisibility();
        })
        .catch(err => {
            console.error("Fetch error:", err);
        });

    commandBuffer = ""
}

// caret stuff

let cachedCharWidth = null;

function updateCaretVisibility() {
    if (!shellState.allowingInput) {
        caretElement.style.display = "none";
    } else caretElement.style.display = "inline-flex";
}

function updateCaretVisualPosition() {
    updateCaretVisibility();

    const lineHeight = parseFloat(window.getComputedStyle(currentLineInputElement).lineHeight);
    const lineWidth = currentLineElement.getBoundingClientRect().width;
    const totalCharsBeforeCaret = currentStaticLineElement.textContent.length + caretPosition;
    const charsPerLine = Math.floor(lineWidth / getCharWidth());
    const lineNumber = Math.floor(totalCharsBeforeCaret / charsPerLine)
    const columnNumber = totalCharsBeforeCaret % charsPerLine;

    const magicOffset = -3; // manually fix alignment
    caretElement.style.left = `${columnNumber * cachedCharWidth + magicOffset}px`;
    caretElement.style.top = `${lineNumber * lineHeight}px`;
}

function getCharWidth() {
    if (cachedCharWidth === null) {
        const measurer = document.createElement("span");
        measurer.style.visibility = "hidden";
        measurer.style.position = "absolute";
        measurer.style.whiteSpace = "pre";
        measurer.style.fontFamily = window.getComputedStyle(currentLineInputElement).fontFamily;
        measurer.style.fontSize = window.getComputedStyle(currentLineInputElement).fontSize;
        measurer.textContent = "3"; // any char
        currentLineElement.appendChild(measurer);
        cachedCharWidth = measurer.getBoundingClientRect().width;
        currentLineElement.removeChild(measurer);
    }

    return cachedCharWidth;
}

function getLine() {
    const lineWidth = currentLineElement.getBoundingClientRect().width;

    const totalCharsBeforeCaret = currentStaticLineElement.textContent.length + caretPosition;
    const charsPerLine = Math.floor(lineWidth / getCharWidth());

    return Math.floor(totalCharsBeforeCaret / charsPerLine)
}

window.addEventListener("resize", updateCaretVisualPosition);

//

async function printMOTD() {
    const motdLines = await fetch('/shell/motd')
        .then(response => {
            if (!response.ok) throw new Error(`HTTP error ${response.status}`);
            return response.json();
        })
        .then(data => {
            return data;
        })
        .catch(err => {
            console.error("Fetch error:", err);
        });

    motdLines.forEach(line => {
        newLine(line);
    })
}

async function updateState() {
    shellState = await fetch('/shell/state', {
        credentials: "include"
    })
        .then(response => {
            if (!response.ok) throw new Error(`HTTP error ${response.status}`);
            return response.json();
        })
        .then(data => {
            console.log(data)
            return data;
        })
        .catch(err => {
            console.error("Fetch error:", err);
        });

}

async function resetSession() {
    await fetch('/shell/resetSession', {
        method: 'POST',
        credentials: 'include'
    }).catch(err => {
        console.error("Fetch error:", err);
    });
}

async function login() {
    await fetch('/shell/login', {
        method: 'POST',
        credentials: 'include'
    }).catch(err => {
        console.error("Fetch error:", err);
    });
}



// entry point
async function main() {
    // reload session on app start
    await resetSession();

    // get initial state
    await updateState();

    await printMOTD();

    // login after printing MOTD to not update last login time before it's printed
    await login();

    // init starting line
    newLine(STATIC_INPUT_COMMAND_START());
}

main();



