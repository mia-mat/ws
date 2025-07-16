package ws.mia.shell;

import jakarta.servlet.http.HttpSession;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import ws.mia.controller.RootController;
import ws.mia.service.ProfileService;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/shell")
public class ShellController {

	private final ShellService shellService;

	ProfileService profileService;

	public ShellController(ShellService shellService, ProfileService profileService) {
		this.shellService = shellService;
		this.profileService = profileService;
	}

	@RequestMapping("")
	public String getSite() {
		return "shell";
	}

	public ShellSession getOrCreateShellSession(HttpSession session) {
		ShellSession shellSession = (ShellSession) session.getAttribute("shellSession");
		if (shellSession == null) {
			shellSession = new ShellSession(profileService.isProd());
			session.setAttribute("shellSession", shellSession);
		}

		return shellSession;
	}

	// set last login time
	@PostMapping("/login")
	@ResponseBody
	public void login(HttpSession session) {
		shellService.login();
	}


	@PostMapping("/execute")
	@ResponseBody
	public ResponseEntity<ShellService.CommandResponse> executeCommand(
			@RequestBody String command,
			HttpSession session) {
		ShellSession shellSession = getOrCreateShellSession(session);

		// JS gives us a sanitized input, but having actual \n's etc. is more useful here:
		String unescapedCommand = StringEscapeUtils.unescapeJava(command);
		ShellService.CommandResponse response = shellService.executeCommand(unescapedCommand, shellSession);

		if (response.isGrantedRootAccess()) {
			ResponseCookie cookie = ResponseCookie.from("rootAccessToken", RootController.ACCESS_TOKEN)
					.httpOnly(true)
					.secure(true)
					.path("/")
					.maxAge(3600 * 24) // 1 day
					.build();

			return ResponseEntity.ok()
					.header(HttpHeaders.SET_COOKIE, cookie.toString())
					.body(response);
		}

		return ResponseEntity.ok(response);
	}

	@PostMapping("/suggest")
	@ResponseBody
	public Map<String, Object> suggest(@RequestBody Map<String, Object> body, HttpSession session) {
		ShellSession shellSession = getOrCreateShellSession(session);

		String prefix = (String) body.get("prefix");
		String fullInput = (String) body.get("fullInput");

		List<String> suggestions = shellService.suggest(fullInput, prefix, shellSession);

		return Map.of("suggestions", suggestions);
	}

	@GetMapping("/state")
	@ResponseBody
	public ShellState getState(HttpSession session) {
		return getOrCreateShellSession(session).getState();
	}

	@GetMapping("/motd")
	@ResponseBody
	public List<String> getLastLogin() {
		return shellService.getMOTD();
	}

	@PostMapping("/resetSession")
	@ResponseBody
	public void resetSession(HttpSession session) {
		session.invalidate();
	}

}
