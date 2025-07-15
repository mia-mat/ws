package ws.mia.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ws.mia.service.ShellService;

@RequestMapping("/")
@Controller
public class RootController {

	private final ShellService shellService;

	public RootController(ShellService shellService) {
		this.shellService = shellService;
	}

	@GetMapping
	public String getSite() {
		return "shell";
	}

	@PostMapping("/execute")
	@ResponseBody
	public String executeCommand(@RequestBody String command) {
		// Simulate shell execution
		return "You typed: " + command;
	}

}
