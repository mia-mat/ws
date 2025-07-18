package ws.mia.util;

import jakarta.servlet.http.HttpServletRequest;

public class RequestUtil {

	// basic but mostly works, especially for this scope.
	public static boolean isMobile(String userAgent) {
		if (userAgent == null) return false;
		String ua = userAgent.toLowerCase();
		return ua.contains("mobi")
				|| ua.contains("android")
				|| ua.contains("iphone")
				|| ua.contains("ipad")
				|| ua.contains("phone");
	}

	public static boolean isMobile(HttpServletRequest request) {
		return isMobile(request.getHeader("User-Agent"));
	}

	public static String getClientAddress(HttpServletRequest request) {
		// try to find de-proxied IP from cloudflare
		String ip = request.getHeader("CF-Connecting-IP"); // cloudflare specific

		if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getHeader("X-Forwarded-For");
			if (ip != null && !ip.isEmpty()) {
				ip = ip.split(",")[0].trim(); // take the first IP in case of multiple
			}
		}

		// if cloudflare forwarded ip not found, just give back the remote address
		if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getRemoteAddr();
		}

		return ip;
	}

	// discord scrapes for OG tags etc., check if the user agent is from them
	public static boolean isDiscord(String userAgent) {
		return (userAgent != null && userAgent.contains("Discordbot"));
	}

	public static boolean isDiscord(HttpServletRequest request) {
		return isDiscord(request.getHeader("User-Agent"));
	}

}
