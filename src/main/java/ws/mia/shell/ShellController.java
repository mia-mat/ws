package ws.mia.shell;

import jakarta.servlet.http.HttpSession;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
public class ShellController {

	private final ShellService shellService;

	public ShellController(ShellService shellService) {
		this.shellService = shellService;
	}

	@RequestMapping("/")
	public String getSite() {
		shellService.login();

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

	@PostMapping("/shell/execute")
	@ResponseBody
	public ShellService.CommandResponse executeCommand(@RequestBody String command, HttpSession session) {
		ShellSession shellSession = getOrCreateShellSession(session);

		// JS gives us a sanitized input, but having actual \n's etc. is more useful here:
		String unescapedCommand = StringEscapeUtils.unescapeJava(command);
		return shellService.executeCommand(unescapedCommand, shellSession);
	}

	@GetMapping("/shell/state")
	@ResponseBody
	public ShellState getState(HttpSession session) {
		return getOrCreateShellSession(session).getState();
	}

	@GetMapping("/shell/motd")
	@ResponseBody
	public List<String> getLastLogin() {
		return shellService.getMOTD();
	}

	@PostMapping("/shell/resetSession")
	public void resetSession(HttpSession session) {
		session.invalidate();
	}

}
