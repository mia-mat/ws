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

}
