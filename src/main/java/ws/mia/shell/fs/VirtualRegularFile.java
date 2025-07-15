package ws.mia.shell.fs;

// as opposed to a directory
public class VirtualRegularFile extends VirtualFile {
	private String content;

	public VirtualRegularFile(String name, String content) {
		super(name);
		this.content = content;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	@Override
	public boolean isDirectory() {
		return false;
	}
}
