package ws.mia.shell;

public class ShellState {

	private boolean allowingInput;
	private String currentDirectory;

	public ShellState() {
		this.allowingInput = true;
		this.currentDirectory = "/home/user";
	}

	public boolean isAllowingInput() {
		return allowingInput;
	}

	public String getCurrentDirectory() {
		return currentDirectory;
	}

	public void setAllowingInput(boolean allowingInput) {
		this.allowingInput = allowingInput;
	}

	public void setCurrentDirectory(String currentDirectory) {
		this.currentDirectory = currentDirectory;
	}
}
