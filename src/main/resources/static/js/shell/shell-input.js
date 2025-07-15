const STATIC_INPUT_COMMAND_START = "[user@mia.ws ~]# "
const STATIC_INPUT_CONTINUED = "> "

const endsWithUnescapedBackslash = str => /(?<!\\)(?:\\\\)*\\$/.test(str);

const linesContainer = document.getElementById("shell");

const caretElement = document.getElementById("shell-caret");

let currentLineElement;
let currentLineInputElement;

let commandBuffer = ""; // pushes to this if end of text contains an un-escaped \, instead of executing.

const keyHandlers = {
    Backspace: () => {
        currentLineInputElement.textContent = currentLineInputElement.textContent.slice(0, -1);
    },
    Enter: async () => {
        if(endsWithUnescapedBackslash(currentLineInputElement.textContent)) {
            commandBuffer += currentLineInputElement.textContent.slice(0, -1);
            newLine(STATIC_INPUT_CONTINUED);
            return;
        }

        // exec command
        await execCurrentLine();
        newLine(STATIC_INPUT_COMMAND_START); // check if last char is '\' for continued, and add support for continuations through a buffer
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
        appendToCurrentLine(e.key);
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

    await fetch('/executeShellCommand', {
        method: 'POST',
        headers: {
            'Content-Type': 'text/plain',  // since your @RequestBody is String, plain text is fine
        },
        body: commandBuffer
    })
        .then(response => {
            if (!response.ok) throw new Error("HTTP error " + response.status);
            return response.text();
        })
        .then(result => {
            newLine("");
            appendToCurrentLine(result);
        })
        .catch(err => {
            console.error("Fetch error:", err);
        });

    commandBuffer = ""
}

// init first line
newLine(STATIC_INPUT_COMMAND_START);
