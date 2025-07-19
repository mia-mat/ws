package ws.mia.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ws.mia.service.*;
import ws.mia.util.RequestUtil;

import java.util.UUID;

@Controller
@RequestMapping("/")
public class RootController {

	// only allow connections to root with knowledge of this token. This makes the user go through shell, since that auto-fills it.
	public static final String ACCESS_TOKEN = UUID.randomUUID().toString();

	private final UptimeService uptimeService;
	private final GitHubService gitHubService;
	private final DatabaseService databaseService;
	private final ProfileService profileService;
	private final LoginService loginService;

	public RootController(LoginService loginService, UptimeService uptimeService, GitHubService gitHubService, DatabaseService databaseService, ProfileService profileService, ProfileService profileService1) {
		this.loginService = loginService;
		this.uptimeService = uptimeService;
		this.gitHubService = gitHubService;
		this.databaseService = databaseService;
		this.profileService = profileService1;
	}

	@GetMapping
	public String getRoot(@CookieValue(value = "rootAccessToken", defaultValue = "none") String token, HttpServletRequest request, Model model) {

		if (RequestUtil.isDiscord(request)) return "og"; // for discord scraping OG tags.

		boolean isMobile = RequestUtil.isMobile(request);

		if (profileService.isProd() // if in dev, just go to shell manually. Typing the command every time can get annoying.
				&& !isMobile
				&& (token == null || !token.equals(ACCESS_TOKEN))) {
			return "redirect:/shell";
		}

		// terminal widget info
		model.addAttribute("lastLoginTime", loginService.getShellFormattedLastLoginTime());
		model.addAttribute("lastLoginAddress", loginService.getLastLoginAddress());
		model.addAttribute("githubUrl", "github.com/mia-mat/");
		model.addAttribute("repoCount", gitHubService.getOrFetchPublicRepositories().size()); // could potentially do this in the background and push with a websocket/GET immediately from js
		model.addAttribute("uptime", uptimeService.getFormattedUptime());
		model.addAttribute("uptimeSeconds", uptimeService.getUptime().getSeconds());

		model.addAttribute("isMobile", isMobile);

		loginService.login();

		return "root";
	}

	@PostMapping("/postMessage")
	@ResponseBody
	public void postMessage(@RequestParam("name") String name,
							@RequestParam("email") String email,
							@RequestParam("message") String message,
							Model model, HttpServletRequest request) {
		if (email == null || message == null || name == null) return;

		databaseService.insertMessage(RequestUtil.getClientAddress(request), name, email, message);
	}


}
