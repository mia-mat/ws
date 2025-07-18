// dynamically update uptime every second

// code pretty much copied from java, but it's not like I'm going to poll the server for a new string every second :p
function formatUptime(seconds) {
    let days = Math.floor(seconds / (24 * 3600));
    seconds %= (24 * 3600);
    let hours = Math.floor(seconds / 3600);
    seconds %= 3600;
    let minutes = Math.floor(seconds / 60);
    seconds %= 60;

    const parts = [];
    if (days > 0) parts.push(`${days} day${days > 1 ? 's' : ''}`);
    if (hours > 0) parts.push(`${hours} hour${hours > 1 ? 's' : ''}`);
    if (minutes > 0) parts.push(`${minutes} min${minutes > 1 ? 's' : ''}`);
    if (parts.length === 0) parts.push(`${seconds} sec${seconds !== 1 ? 's' : ''}`);
    return parts.join(', ');
}

const uptimeEl = document.getElementById('uptime');
let uptimeSeconds = parseInt(uptimeEl.getAttribute('data-uptime-seconds'));

function updateUptimeDisplay() {
    uptimeEl.textContent = formatUptime(uptimeSeconds++);
}

updateUptimeDisplay();
setInterval(updateUptimeDisplay, 1000);