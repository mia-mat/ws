package ws.mia.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import ws.mia.service.ShellService;

import java.util.List;

@Controller
public class ShellController {

	private final ShellService shellService;

	public ShellController(ShellService shellService) {
		this.shellService = shellService;
	}

	@RequestMapping("/")
	public String getSite(HttpServletRequest request) {
		shellService.login(request);

		return "shell";
	}

	@PostMapping("/shell/execute")
	@ResponseBody
	public ShellService.CommandResponse executeCommand(@RequestBody String command) {
		// JS gives us a sanitized input, but having actual \n's etc. is more useful here:
		String unescapedCommand = StringEscapeUtils.unescapeJava(command);
		return shellService.executeCommand(unescapedCommand);
	}

	@GetMapping("/shell/motd")
	@ResponseBody
	public List<String> getLastLogin() {
		return shellService.getMOTD();
	}

}
