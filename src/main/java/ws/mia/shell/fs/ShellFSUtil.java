package ws.mia.shell.fs;

public class ShellFSUtil {

	public static void recursivelyDelete(VirtualFile parent) {
		parent.setExists(false);
		parent.setParent(null);
		if(parent.isDirectory()) {
			VirtualDirectory parentDir = ((VirtualDirectory)parent);
			for(VirtualFile f : parentDir.getChildren().values()) {
				recursivelyDelete(f);
			}

			parentDir.clearChildren();
		}
	}

}
