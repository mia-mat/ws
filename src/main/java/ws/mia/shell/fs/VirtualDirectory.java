package ws.mia.shell.fs;

import java.util.HashMap;
import java.util.Map;

public class VirtualDirectory extends VirtualFile{
	private Map<String, VirtualFile> children = new HashMap<>();

	public VirtualDirectory(String name) {
		super(name);
	}

	public Map<String, VirtualFile> getChildren() {
		return children;
	}

	public void addChild(VirtualFile child) {
		child.setParent(this);
		children.put(child.getName(), child);
	}

	public VirtualFile getChild(String name) {
		return children.get(name);
	}

	@Override
	public boolean isDirectory() {
		return true;
	}
}
