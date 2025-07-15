package ws.mia.shell;

import jakarta.servlet.http.HttpSession;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/shell")
public class ShellController {

	private final ShellService shellService;

	public ShellController(ShellService shellService) {
		this.shellService = shellService;
	}

	@RequestMapping("")
	public String getSite() {
		return "shell";
	}

	public ShellSession getOrCreateShellSession(HttpSession session) {
		ShellSession shellSession = (ShellSession) session.getAttribute("shellSession");
		if (shellSession == null) {
			shellSession = new ShellSession();
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
	public ShellService.CommandResponse executeCommand(@RequestBody String command, HttpSession session) {
		ShellSession shellSession = getOrCreateShellSession(session);

		// JS gives us a sanitized input, but having actual \n's etc. is more useful here:
		String unescapedCommand = StringEscapeUtils.unescapeJava(command);
		return shellService.executeCommand(unescapedCommand, shellSession);
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
