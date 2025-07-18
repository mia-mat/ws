package ws.mia.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ws.mia.service.DatabaseService;
import ws.mia.service.GitHubService;
import ws.mia.service.LoginService;
import ws.mia.service.UptimeService;
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


	private LoginService loginService;

	public RootController(LoginService loginService, UptimeService uptimeService, GitHubService gitHubService, DatabaseService databaseService) {
		this.loginService = loginService;
		this.uptimeService = uptimeService;
		this.gitHubService = gitHubService;
		this.databaseService = databaseService;
	}

	@GetMapping
	public String getRoot(@CookieValue(value = "rootAccessToken", defaultValue = "none") String token, HttpServletRequest request, Model model) {
		
		if (!((token != null && token.equals(ACCESS_TOKEN))
				|| RequestUtil.isMobile(request))) {
			return "redirect:/shell";
		}

		// terminal widget info
		model.addAttribute("lastLoginTime", loginService.getShellFormattedLastLoginTime());
		model.addAttribute("lastLoginAddress", loginService.getLastLoginAddress());
		model.addAttribute("githubUrl", "github.com/mia-mat/");
		model.addAttribute("repoCount", gitHubService.getOrFetchPublicRepositories().size()); // could potentially do this in the background and push with a websocket/GET immediately from js
		model.addAttribute("uptime", uptimeService.getFormattedUptime());
		model.addAttribute("uptimeSeconds", uptimeService.getUptime().getSeconds());

		model.addAttribute("mobile", RequestUtil.isMobile(request));

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
