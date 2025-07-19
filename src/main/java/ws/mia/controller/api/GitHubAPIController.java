package ws.mia.controller.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ws.mia.service.GitHubService;

@RestController
@RequestMapping("/api")
public class GitHubAPIController {


	private final GitHubService gitHubService;

	public GitHubAPIController(GitHubService gitHubService) {
		this.gitHubService = gitHubService;
	}

	@GetMapping("/repoCount")
	public int getRepoCount() {
		return gitHubService.getOrFetchPublicRepositories().size();
	}

}
