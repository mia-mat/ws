package ws.mia.shell;

public class ShellSession {

	private ShellState state;

	public ShellSession(boolean isProd) {
		this.state = new ShellState(isProd);
	}

	public ShellState getState() {
		return state;
	}

}
