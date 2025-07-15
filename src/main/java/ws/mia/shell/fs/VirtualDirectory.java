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

	public void removeChild(String name) {
		VirtualFile removed = children.remove(name);
		if (removed != null) {
			removed.setParent(null);
		}
	}

	public VirtualFile getChild(String name) {
		return children.get(name);
	}

	@Override
	public boolean isDirectory() {
		return true;
	}

	// dangerous
	protected void clearChildren() {
		this.children.clear();
	}
}
