package ws.mia.shell;

import ws.mia.service.GitHubService;

public class ShellSession {

	private ShellState state;


	public ShellSession(GitHubService gitHubService, boolean isProd) {
		this.state = new ShellState(gitHubService, isProd);
	}

	public ShellState getState() {
		return state;
	}

}
