package ws.mia.shell.fs;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import ws.mia.service.GitHubService;
import ws.mia.shell.ShellService;
import ws.mia.shell.ShellState;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

public class ShellFSUtil {

	public static void recursivelyDelete(VirtualFile parent) {
		parent.setExists(false);
		parent.setParent(null);
		if (parent.isDirectory()) {
			VirtualDirectory parentDir = ((VirtualDirectory) parent);
			for (VirtualFile f : parentDir.getChildren().values()) {
				recursivelyDelete(f);
			}

			parentDir.clearChildren();
		}
	}

	// pulls from github to generate the projects directory in a state
	public static void fetchAndGenerateProjectsDirectory(ShellState state, String projectsPath, GitHubService github) {
		// check disk to see if the repos exist
		File projectsFolderOnDisk = ShellState.ROOT_INIT_PATH.resolve(projectsPath).toFile();
		if (!projectsFolderOnDisk.isDirectory() || !projectsFolderOnDisk.exists()) {
			throw new RuntimeException("Could not find projects directory on disk");
		}

		projectsPath = "/" + projectsPath; // normalize for virtual fs, since it needs a / prefix, while .resolve requires a non / prefix

		VirtualFile virtualProjectFile = state.getVirtualFile(projectsPath);
		if (virtualProjectFile == null || !virtualProjectFile.isDirectory() && !virtualProjectFile.exists()) {
			throw new RuntimeException("Virtual projects directory is not a directory");
		}

		VirtualDirectory virtualProjectsDir = (VirtualDirectory) virtualProjectFile;

		UsernamePasswordCredentialsProvider ghCreds = github.getCredentials();

		Collection<GitHubService.Repository> repos = github.getOrFetchPublicRepositories();
		repos.forEach(repo -> {
			String repoName = repo.getName();
			File repoDir = new File(projectsFolderOnDisk, repoName);
			try {
				if (repoDir.exists() && new File(repoDir, ".git").exists()) {
					FileRepositoryBuilder builder = new FileRepositoryBuilder();
					Repository localRepo = builder.setGitDir(new File(repoDir, ".git"))
							.readEnvironment()
							.findGitDir()
							.build();

					try (Git git = new Git(localRepo)) {
						if (ghCreds != null) {
							git.fetch().setCredentialsProvider(ghCreds).call();
						} else git.fetch().call();


						// compare local HEAD and remote HEAD
						String localBranch = "refs/heads/" + repo.getDefaultBranch();
						String remoteBranch = "refs/remotes/origin/" + repo.getDefaultBranch();

						Object localHead = localRepo.resolve(localBranch);
						Object remoteHead = localRepo.resolve(remoteBranch);

						if (localHead == null || remoteHead == null || localHead.equals(remoteHead)) {
							return; // up to date
						}

						if (ghCreds != null) {
							git.pull().setCredentialsProvider(ghCreds).call();
						} else git.pull().call();
						return;
					}
				} else { // repo doesn't exist on disk
					CloneCommand clone = Git.cloneRepository()
							.setURI(repo.getCloneUrl())
							.setDirectory(repoDir);
					if (ghCreds != null) clone.setCredentialsProvider(ghCreds);
					clone.call();

				}

				// on-disk is up to date. Now update the shell state:
				virtualProjectsDir.addChild(loadFromDisk(repoName, repoDir.toPath()));

			} catch (IOException | GitAPIException e) {
				throw new RuntimeException(e);
			}

		});


		repos.forEach(repo -> {

		});

	}


	public static VirtualDirectory loadFromDisk(String name, Path path) throws IOException {
		VirtualDirectory dir = new VirtualDirectory(name);

		try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
			for (Path entry : stream) {
				String entryName = entry.getFileName().toString();
				if (Files.isDirectory(entry)) {
					dir.addChild(loadFromDisk(entryName, entry));
				} else {
					byte[] contentBytes = Files.readAllBytes(entry);
					String content = new String(contentBytes, StandardCharsets.ISO_8859_1);
					VirtualRegularFile file = new VirtualRegularFile(entryName, content);

					if (entry.equals(ShellState.LAUNCH_UI_FILE_PATH)) { // for launching into root, we need to identify this file if it gets moved etc.
						file.addAttribute(ShellService.LAUNCH_UI_ATTRIBUTE);
					}

					dir.addChild(file);
				}
			}
		}

		return dir;
	}

}
