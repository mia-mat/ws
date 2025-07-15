package ws.mia.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Random;

@Service
public class ShellService {

	DateTimeFormatter MOTD_DATE_FORMATTER = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss yyyy", Locale.ENGLISH);

	private long lastLoginTimestamp = System.currentTimeMillis();

	private String lastLoginAddress = "192.168.0.1"; // this will be a cloudflare ip, security is fine :p

	public void login(HttpServletRequest request) {
		lastLoginTimestamp = System.currentTimeMillis();

		// if proxied through cloudflare, request should have these proxies.
		// just generate a fake random one otherwise to not accidentally expose an IP.
		if (request.getHeader("CF-Connecting-IP") != null
				|| request.getHeader("X-Forwarded-For") != null
				|| request.getHeader("X-Real-IP") != null) {
			Random random = new Random();
			// subnets higher than 16 look kinda weird
			lastLoginAddress = "192.168." + random.nextInt(17) + "." + random.nextInt(256);
		} else {
			lastLoginAddress = request.getRemoteAddr();
		}

	}

	public List<String> getMOTD() {
		String formattedTime = Instant.ofEpochMilli(lastLoginTimestamp).atZone(ZoneId.systemDefault()).format(MOTD_DATE_FORMATTER);

		return List.of("Last Login: " + formattedTime + " from " + lastLoginAddress);
	}

}
