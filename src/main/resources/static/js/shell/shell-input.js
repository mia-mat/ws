const STATIC_INPUT_COMMAND_START = "[user@mia.ws ~]# "
const STATIC_INPUT_CONTINUED = "> "

const endsWithUnescapedBackslash = str => /(?<!\\)(?:\\\\)*\\$/.test(str);

const linesContainer = document.getElementById("shell");

const caretElement = document.getElementById("shell-caret");

let currentLineElement;
let currentLineInputElement;

// appended to the top
let commandHistory = []; // technically this should be dependent on context, but that's not too important here.
let inputHistoryPosition = 0; // index 0 represents the currently typed command

let commandBuffer = ""; // pushes to this if end of text contains an un-escaped \, instead of executing.
let continuing = 0; // how many lines has the user been continuing the command for: used to rectify command history

const keyHandlers = {

    Backspace: () => {
        currentLineInputElement.textContent = currentLineInputElement.textContent.slice(0, -1);
        // todo selection
    },
    Enter: async () => {
        if(commandHistory[0] !== "") {
            commandHistory.unshift("");
        }
        inputHistoryPosition = 0;

        if(endsWithUnescapedBackslash(currentLineInputElement.textContent)) {
            commandBuffer += currentLineInputElement.textContent.slice(0, -1);
            newLine(STATIC_INPUT_CONTINUED);
            continuing++;
            return;
        }

        // exec command
        await execCurrentLine();
        newLine(STATIC_INPUT_COMMAND_START); // check if last char is '\' for continued, and add support for continuations through a buffer
    },
    ArrowUp: () => {
        if(commandHistory.length <= inputHistoryPosition+1) return;
        inputHistoryPosition++;
        currentLineInputElement.textContent = commandHistory[inputHistoryPosition];
    },
    ArrowDown: () => {
        if(inputHistoryPosition <= 0) return;
        inputHistoryPosition--;
        currentLineInputElement.textContent = commandHistory[inputHistoryPosition];
    },
    Tab: () => {
        // todo display possibilities
        appendToCurrentLine("\t");
    },
};

window.addEventListener('keydown', function (e) {
    e.preventDefault();

    if (keyHandlers[e.key]) {
        keyHandlers[e.key]();
    } else if (e.key.length === 1 && !e.metaKey && !e.ctrlKey) {
        appendInputToCurrentLine(e.key);
    }

});


function newLine(staticContent) {
    currentLineElement = document.createElement('div')
    currentLineElement.classList.add("shell-line")
    linesContainer.appendChild(currentLineElement);

    const staticLineText = document.createElement('div');
    staticLineText.classList.add("shell-line-static")
    currentLineElement.appendChild(staticLineText);

    staticLineText.textContent = staticContent;

    currentLineInputElement = document.createElement('div');
    currentLineInputElement.classList.add("shell-line-input")
    currentLineElement.appendChild(currentLineInputElement);

    currentLineElement.appendChild(caretElement)
}

function appendInputToCurrentLine(textToAppend) {
    appendToCurrentLine(textToAppend);

    // If user changes a command in history, just take them back to position 0, as we don't want to edit history.
    // This is slightly different from what tools like cmd.exe do (wherein they store a buffer of edits until executed),
    // but it's good enough here.
    inputHistoryPosition = 0;
    commandHistory[0] = currentLineInputElement.textContent;
}

function appendToCurrentLine(textToAppend) {
    // replace leading spaces with nbsp's to render them (wacky browser stuff)
    if (/^[\u00A0\s]*$/.test(textToAppend) &&
        (currentLineInputElement.textContent === "" || /^[\u00A0\s]*$/.test(currentLineInputElement.textContent))) {
        currentLineInputElement.textContent += "\u00A0";
    } else {
        currentLineInputElement.textContent += textToAppend;
    }
}

async function execCurrentLine() {
    commandBuffer+=currentLineInputElement.textContent;

    // replace history with command buffer
    if(continuing !== 0) {
        commandHistory.splice(1, continuing, commandBuffer);
        continuing = 0;
    }

    await fetch('/shell/execute', {
        method: 'POST',
        headers: {
            'Content-Type': 'text/plain',  // since your @RequestBody is String, plain text is fine
        },
        body: commandBuffer
    })
        .then(response => {
            if (!response.ok) throw new Error(`HTTP error ${response.status}`);
            return response.json();
        })
        .then(result => {
            result.forEach(line => {
                newLine("");
                appendToCurrentLine(line);
            })
        })
        .catch(err => {
            console.error("Fetch error:", err);
        });

    commandBuffer = ""
}

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

// entry point
async function main() {
    await printMOTD();

    // init starting line
    newLine(STATIC_INPUT_COMMAND_START);
}

main();


