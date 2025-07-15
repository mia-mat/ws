package ws.mia.controller;

import jakarta.servlet.http.HttpServletRequest;
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

	@PostMapping("/executeShellCommand")
	@ResponseBody
	public String executeCommand(@RequestBody String command) {
		// ignore leading and trailing whitespace
		command = command.replaceFirst("^\\s+", "").trim();
		String[] args = command.split(" ");

		if (args[0].equals("echo")) {
			return command.substring(args[0].length() + 1).replaceFirst("^\\s+", "").trim();
		} else {
			return "-bash: " + args[0] + ": command not found";
		}

		// have a fake neofetch/fastfetch
		// exit to just stop input

	}

	@GetMapping("/shellMOTD")
	@ResponseBody
	public List<String> getLastLogin() {
		return shellService.getMOTD();
	}

}
