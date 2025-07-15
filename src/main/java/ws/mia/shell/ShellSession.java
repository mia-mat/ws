package ws.mia.shell;

public class ShellSession {

	private ShellState state;

	public ShellSession() {
		this.state = new ShellState();
	}

	public ShellState getState() {
		return state;
	}

}
