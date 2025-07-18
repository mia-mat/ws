package ws.mia.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Random;

@Service
public class LoginService {

	private static final DateTimeFormatter SHELL_FORMATTER = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss yyyy", Locale.ENGLISH);

	private long lastLoginTimestamp = System.currentTimeMillis();

	private String lastLoginAddress = "192.168.0.1"; // fake IP

	public void login() {
		lastLoginTimestamp = System.currentTimeMillis();

		// just generate a fake random one to not accidentally expose an IP.
		Random random = new Random();
		// subnets higher than 16 look kinda weird
		lastLoginAddress = "192.168." + random.nextInt(17) + "." + random.nextInt(256);
	}


	public long getLastLoginTimestamp() {
		return lastLoginTimestamp;
	}

	public String getLastLoginAddress() {
		return lastLoginAddress;
	}

	public String getShellFormattedLastLoginTime() {
		return Instant.ofEpochMilli(getLastLoginTimestamp())
				.atZone(ZoneId.systemDefault())
				.format(SHELL_FORMATTER);
	}
}
