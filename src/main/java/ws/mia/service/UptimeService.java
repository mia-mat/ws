package ws.mia.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class UptimeService {

	private final Instant startTime;

	public UptimeService() {
		this.startTime = Instant.now();
	}

	public Duration getUptime() {
		return Duration.between(startTime, Instant.now());
	}

	public String getFormattedUptime() {
		long seconds = getUptime().getSeconds();

		long days = seconds / (24 * 3600);
		seconds %= (24 * 3600);
		long hours = seconds / 3600;
		seconds %= 3600;
		long minutes = seconds / 60;
		seconds %= 60;

		List<String> parts = new ArrayList<>();
		if (days > 0) parts.add(days + " day" + (days > 1 ? "s" : ""));
		if (hours > 0) parts.add(hours + " hour" + (hours > 1 ? "s" : ""));
		if (minutes > 0) parts.add(minutes + " min" + (minutes > 1 ? "s" : ""));
		if (seconds > 0 && parts.isEmpty()) // only show seconds if nothing else is shown
			parts.add(seconds + " sec" + (seconds > 1 ? "s" : ""));

		return String.join(", ", parts);
	}
}