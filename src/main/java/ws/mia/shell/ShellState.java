package ws.mia.shell;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ws.mia.shell.fs.VirtualDirectory;
import ws.mia.shell.fs.VirtualFile;
import ws.mia.shell.fs.VirtualRegularFile;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ShellState {

	private static Path ROOT_INIT_PATH;
	private static Path LAUNCH_UI_FILE_PATH;

	private String username;
	private boolean allowingInput;

	private String currentDirectory;
	@JsonIgnore
	private final VirtualDirectory filesystem; // pointer to the root directory

	public ShellState(boolean isProd) {
		if(!isProd) {
			ROOT_INIT_PATH = Paths.get("src/main/resources/initial-fs").toAbsolutePath();
		} else {
			ROOT_INIT_PATH = Paths.get("initial-fs");
		}

		LAUNCH_UI_FILE_PATH = ROOT_INIT_PATH.resolve("home/user/launch-ui.sh");


		this.username = "user";
		this.allowingInput = true;
		this.filesystem = createDefaultRoot();
		this.currentDirectory = "/home/"+username;
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

	public String getUsername() {
		return username;
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
					VirtualRegularFile file = new VirtualRegularFile(entryName, content);
					if(entry.equals(LAUNCH_UI_FILE_PATH)) {
						file.addAttribute(ShellService.LAUNCH_UI_ATTRIBUTE);
					}
					dir.addChild(file);
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

		if (path.startsWith("~")) {
			if (path.equals("~") || path.startsWith("~/")) {
				path = "/home/"+username + path.substring(1);
			} else {
				return null; // not a real file, invalid syntax
			}
		}

		String[] parts = path.split("/");

		VirtualFile current = getFilesystem();
		for (int i = 1; i < parts.length; i++) {
			if (!(current instanceof VirtualDirectory dir)) {
				return null; // trying to descend into a non-directory
			}
			current = dir.getChild(parts[i]);

			if (current == null || !current.exists()) {
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

		if (relativePath.equals("~") || relativePath.startsWith("~/")) {
			relativePath = "/home/" + username + relativePath.substring(1); // absolute
		} else {
			Path basePath = Paths.get(currentDirectory);
			Path resolvedPath = basePath.resolve(relativePath).normalize();
			relativePath = resolvedPath.toString().replace("\\", "/");

			if (!relativePath.startsWith("/")) {
				relativePath = "/" + relativePath;
			}
		}

		return getVirtualFile(relativePath);
	}

	@JsonIgnore
	public VirtualDirectory getCurrentVirtualDirectory() {
		VirtualFile file = getVirtualFile(getCurrentDirectory());
		if(file == null || !file.exists()) return null;
		if(file instanceof VirtualDirectory) {
			return (VirtualDirectory) file;
		}
		throw new RuntimeException("currentDirectory is a file!");
	}



}
