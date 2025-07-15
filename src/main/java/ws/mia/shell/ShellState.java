package ws.mia.shell;

import ws.mia.shell.fs.VirtualDirectory;
import ws.mia.shell.fs.VirtualFile;
import ws.mia.shell.fs.VirtualRegularFile;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ShellState {
	private static final Path ROOT_INIT_PATH = Paths.get("src/main/resources/initial-fs").toAbsolutePath();

	private boolean allowingInput;

	private String currentDirectory;
	private final VirtualDirectory filesystem; // pointer to the root directory

	public ShellState() {
		this.allowingInput = true;
		this.filesystem = createDefaultRoot();
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
		this.currentDirectory = currentDirectory.replace("\\", "/");
	}

	public void setCurrentDirectory(VirtualDirectory directory) {
		setCurrentDirectory(directory.getPath());
	}

	public VirtualDirectory getFilesystem() {
		return filesystem;
	}

	private VirtualDirectory createDefaultRoot() {
		try {
			return loadFromDisk(ROOT_INIT_PATH.getFileName().toString(), ROOT_INIT_PATH);
		} catch (IOException e) {
			throw new RuntimeException("Failed to load initial virtual file system", e);
		}
	}

	private VirtualDirectory loadFromDisk(String name, Path path) throws IOException {
		VirtualDirectory dir = new VirtualDirectory(name);

		try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
			for (Path entry : stream) {
				String entryName = entry.getFileName().toString();
				if (Files.isDirectory(entry)) {
					dir.addChild(loadFromDisk(entryName, entry));
				} else {
					String content = Files.readString(entry);
					dir.addChild(new VirtualRegularFile(entryName, content));
				}
			}
		}

		return dir;
	}

	public VirtualFile getVirtualFile(String path) {
		if (path == null || !path.startsWith("/")) {
			throw new IllegalArgumentException("Path must be absolute and start with '/'");
		}

		path = path.replace("\\", "/");

		String[] parts = path.split("/");

		VirtualFile current = getFilesystem();

		for (int i = 1; i < parts.length; i++) {
			if (!(current instanceof VirtualDirectory dir)) {
				return null; // trying to descend into a non-directory
			}

			current = dir.getChild(parts[i]);
			if (current == null) {
				return null; // file not found
			}
		}

		return current;
	}

	public VirtualFile getRelativeVirtualFile(String relativePath) {
		if(relativePath.isEmpty()) {
			return getVirtualFile(currentDirectory);
		}

		relativePath = relativePath.replace("\\", "/");

		Path basePath = Paths.get(currentDirectory);
		Path resolvedPath = basePath.resolve(relativePath).normalize();

		String fullPath = resolvedPath.toString().replace("\\", "/");
		if (!fullPath.startsWith("/")) {
			fullPath = "/" + fullPath; // ensure absolute
		}

		return getVirtualFile(fullPath);
	}

	public VirtualDirectory getCurrentVirtualDirectory() {
		VirtualFile file = getVirtualFile(getCurrentDirectory());
		if(file instanceof VirtualDirectory) {
			return (VirtualDirectory) file;
		}
		throw new RuntimeException("currentDirectory is a file!");
	}


}
