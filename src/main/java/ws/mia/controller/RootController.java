package ws.mia.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@Controller
@RequestMapping("/")
public class RootController {

	// only allow connections to root with knowledge of this token. This makes the user go through shell, since that auto-fills it.
	public static final String ACCESS_TOKEN = UUID.randomUUID().toString();

	@GetMapping
	public String redirectToShell(@CookieValue(value = "rootAccessToken") String token) {
		if (token != null && token.equals(ACCESS_TOKEN)) {
			return "root";
		}
		return "redirect:/shell";
	}

}
