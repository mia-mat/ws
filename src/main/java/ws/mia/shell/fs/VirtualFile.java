package ws.mia.shell.fs;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public abstract class VirtualFile implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private String name;

	private boolean exists; // false if file has been deleted. This reference might just still hang around somehwere.

	@JsonIgnore // infinite recursion is not fun
	private VirtualDirectory parent;

	private List<String> attributes;

	public VirtualFile(String name, VirtualDirectory parent) {
		this.name = name;
		this.parent = parent;
		this.exists = true;
		this.attributes = new ArrayList<>();
	}

	public VirtualFile(String name) {
		// parent set by addChild method of VirtualDirectory
		this(name, null);
	}


	public String getName() {
		return name;
	}

	public VirtualDirectory getParent() {
		return parent;
	}

	protected void setParent(VirtualDirectory dir) {
		this.parent = dir;
	}

	public String getPath() {
		if (parent == null) {
			return "/"; // root directory
		}

		LinkedList<String> parts = new LinkedList<>();
		VirtualFile current = this;

		while (current.getParent() != null) {
			parts.addFirst(current.getName());
			current = current.getParent();
		}

		return "/" + String.join("/", parts);
	}

	public abstract boolean isDirectory();

	public void delete() {
		ShellFSUtil.recursivelyDelete(this);
	}

	protected void setExists(boolean newState) {
		this.exists = newState;
	}

	public boolean exists() {
		return exists;
	}

	public boolean setName(String newName, boolean force) {
		if(getParent() != null && getParent().exists()) {
			if(getParent().getChildren().containsKey(newName) && !force) {
				return false;
			} else {
				// direct operations on map to not change other properties
				getParent().getChildren().remove(getName());
				getParent().getChildren().put(newName, this);
			}
		}

		this.name = newName;

		return true;
	}

	public List<String> getAttributes() {
		return attributes;
	}

	public void addAttribute(String a) {
		attributes.add(a);
	}
}
