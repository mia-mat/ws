package ws.mia.shell;

import org.springframework.stereotype.Service;
import ws.mia.SpringApplication;
import ws.mia.service.UptimeService;
import ws.mia.shell.fs.VirtualDirectory;
import ws.mia.shell.fs.VirtualFile;
import ws.mia.shell.fs.VirtualRegularFile;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ShellService {

	public static final String LAUNCH_UI_ATTRIBUTE = "LAUNCH_UI";
	DateTimeFormatter MOTD_DATE_FORMATTER = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss yyyy", Locale.ENGLISH);

	private final UptimeService uptimeService;

	public ShellService(UptimeService uptimeService) {
		this.uptimeService = uptimeService;
	}

	private long lastLoginTimestamp = System.currentTimeMillis();

	private String lastLoginAddress = "192.168.0.1"; // fake IP

	public void login() {
		lastLoginTimestamp = System.currentTimeMillis();

		// just generate a fake random one to not accidentally expose an IP.
		Random random = new Random();
		// subnets higher than 16 look kinda weird
		lastLoginAddress = "192.168." + random.nextInt(17) + "." + random.nextInt(256);
	}

	public List<String> getMOTD() {
		String formattedTime = Instant.ofEpochMilli(lastLoginTimestamp).atZone(ZoneId.systemDefault()).format(MOTD_DATE_FORMATTER);

		return List.of("Last Login: " + formattedTime + " from " + lastLoginAddress);
	}

	// retrieve once
	private static final Map<String, Method> commandMethods;

	static {
		commandMethods = new HashMap<>();
		for (Method method : fetchAllShellCommands()) {
			ShellCommand commandAnnotation = method.getAnnotation(ShellCommand.class);

			if (commandAnnotation.value().length == 0) {
				throw new RuntimeException("Shell command " + method.getName() + " has no aliases");
			}

			for (String s : commandAnnotation.value()) {
				if (commandMethods.containsKey(s)) {
					throw new RuntimeException("Tried to register shell command " + s + " twice");
				}
				commandMethods.put(s, method);
			}
		}
	}

	private static List<Method> fetchAllShellCommands() {
		List<Method> methods = new ArrayList<>();
		for (Method method : ShellService.class.getDeclaredMethods()) {
			if (method.isAnnotationPresent(ShellCommand.class)
					&& Arrays.equals(method.getParameterTypes(), new Class[]{ShellSession.class, String.class, String[].class})) {
				methods.add(method);
			}
		}
		return methods;
	}

	public static class CommandResponse {
		private final List<String> lines;
		private ShellState state;
		private final Boolean grantedRootAccess; // only true if the client will now be redirected to the root

		public CommandResponse(List<String> lines, ShellState state) {
			this.lines = lines;
			this.state = state;
			this.grantedRootAccess = false;
		}

		public CommandResponse(boolean grantedRootAccess) {
			this.grantedRootAccess = grantedRootAccess;
			this.lines = null;
			this.state = null;
		}

		public CommandResponse(List<String> lines) {
			this(lines, null);
		}

		public List<String> getLines() {
			return lines;
		}

		public ShellState getState() {
			return state;
		}

		public void setState(ShellState newState) {
			this.state = newState;
		}

		public boolean isGrantedRootAccess() {
			return grantedRootAccess;
		}
	}

	@SuppressWarnings("unchecked")
	public CommandResponse executeCommand(final String command, ShellSession session) {
		String[] args = command.split(" ");
		for (int i = 0; i < args.length; i++) {
			args[i] = args[i].replaceFirst("^\\s+", "").trim(); // remove whitespace
		}

		if (!commandMethods.containsKey(args[0])) {
			// special case for our launch-ui.sh file
			// in the form of ./FILE_NAME <anything>
			if(command.startsWith("./")) {
				String fileDir = command.split(" ")[0].substring(2);
				VirtualFile file = session.getState().getRelativeVirtualFile(fileDir);
				if(file != null && file.exists() && file.getAttributes().contains(LAUNCH_UI_ATTRIBUTE)) {
					return new CommandResponse(true);
				}
			}

			return new CommandResponse(List.of("-bash: " + args[0] + ": command not found"), session.getState());
		}

		try {
			CommandResponse response = new CommandResponse((List<String>) commandMethods.get(args[0]).invoke(this, session, args[0], Arrays.copyOfRange(args, 1, args.length)));
			response.setState(session.getState()); // state reference modified by method
			return response;
		} catch (Exception e) {
			return new CommandResponse(List.of("error while executing command: " + args[0] + ": " + e.getMessage()));
		}

	}

	@Target(ElementType.METHOD)
	@Retention(RetentionPolicy.RUNTIME)
	@interface ShellCommand {
		String[] value(); // aliases

		String[] argUsages() default {};
	}

	final static String NBSP = "\u00A0"; // used in place of lines of entirely spaces for shell (html) to display properly

	@ShellCommand(value = "echo", argUsages = "[arg ...]")
	private List<String> commandEcho(final ShellSession session, final String commandName, final String[] args) {
		String joinedArgs = String.join(" ", args);
		String[] lines = joinedArgs.split("\n", -1);

		List<String> output = new ArrayList<>();

		for (String line : lines) {
			if (line.isBlank()) {
				output.add(NBSP);
			} else {
				output.add(line);
			}
		}

		// at least one line
		if (output.isEmpty()) {
			output.add(NBSP);
		}
		return output;
	}

	@ShellCommand({"exit", "logout"})
	private List<String> commandExit(final ShellSession session, final String commandName, final String[] args) {
		List<String> output = new ArrayList<>();
		// simulate running logout, as in a real ssh shell
		if (commandName.equals("exit")) output.add("logout");
		output.add("Connection to mia.ws closed.");
		session.getState().setAllowingInput(false);
		return output;
	}

	@ShellCommand("help")
	private List<String> commandHelp(final ShellSession session, final String commandName, final String[] args) {
		List<String> responseList = new ArrayList<>();
		String version = SpringApplication.class.getPackage().getImplementationVersion();
		responseList.add("MIAW bash, version %s-release (x86_64-pc-linux-miaw)".formatted(version));
		responseList.add("These shell commands are defined internally.  Type `help' to see this list.");
		responseList.add(NBSP);

		// avoid duplicate entries by fetching here rather than pulling from map, which has keys for all aliases
		fetchAllShellCommands().stream()
				.sorted(Comparator.comparing(Method::getName))
				.forEach(method -> {
					ShellCommand command = method.getAnnotation(ShellCommand.class);
					StringBuilder commandUsageBuilder = new StringBuilder(command.value()[0]);
					for (String s : command.argUsages()) {
						commandUsageBuilder.append(" ").append(s);
					}
					responseList.add(commandUsageBuilder.toString());
				});

		return responseList;
	}

	@ShellCommand(value = "ls", argUsages = "[path]")
	private List<String> commandLs(final ShellSession session, final String commandName, final String[] args) {
		ShellState state = session.getState();
		VirtualFile target;

		if (args.length > 0) {
			target = state.getRelativeVirtualFile(args[0]);
		} else {
			target = state.getCurrentVirtualDirectory();
		}

		if (target == null || !target.exists()) {
			String filename = "";
			if(args.length > 0) filename = args[0];
			return List.of("ls: cannot access %s: No such file or directory".formatted(filename));
		}

		if (!target.isDirectory()) {
			return List.of(target.getName());
		}

		VirtualDirectory dir = (VirtualDirectory) target;

		return List.of(dir.getChildren().values().stream()
						.filter(VirtualFile::exists)
						.map(VirtualFile::getName)
						.sorted()
						.collect(Collectors.joining("  "))
		);
	}

	@ShellCommand(value = "cat", argUsages = "<path> [path2 ...]")
	private List<String> commandCat(final ShellSession session, final String commandName, final String[] args) {
		if (args.length == 0) {
			return List.of("cat: missing file operand");
		}

		List<String> output = new ArrayList<>();

		for (String rawPath : args) {
			VirtualFile file = session.getState().getRelativeVirtualFile(rawPath);

			if (file == null || !file.exists()) {
				output.add(String.format("cat: %s: No such file or directory", rawPath));
				continue;
			}

			if (file.isDirectory()) {
				output.add(String.format("cat: %s: Is a directory", rawPath));
				continue;
			}

			VirtualRegularFile regularFile = (VirtualRegularFile) file;
			String content = regularFile.getContent();

			String[] lines = content.split("\n", -1);
			output.addAll(Arrays.asList(lines));
		}

		return output;
	}

	@ShellCommand(value = "cd", argUsages = "<path>")
	private List<String> commandCd(final ShellSession session, final String commandName, final String[] args) {
		String concatArgs = String.join(" ", args);

		if (concatArgs.isEmpty()) {
			concatArgs = "/home/" + session.getState().getUsername();
		}

		VirtualFile file = session.getState().getRelativeVirtualFile(concatArgs);

		if (file == null || !file.exists()) {
			return List.of("-bash: cd: %s: No such file or directory".formatted(concatArgs));
		}

		if (!file.isDirectory()) {
			return List.of("-bash: cd: %s: Not a directory".formatted(concatArgs));
		}

		session.getState().setCurrentDirectory((VirtualDirectory) file);
		return List.of();
	}

	@ShellCommand("pwd")
	private List<String> commandPwd(final ShellSession session, final String commandName, final String[] args) {
		return List.of(session.getState().getCurrentDirectory());
	}

	@ShellCommand(value = "mkdir", argUsages = "<path>")
	private List<String> commandMkdir(final ShellSession session, final String commandName, final String[] args) {
		if (args.length == 0) {
			return List.of("mkdir: missing operand");
		}

		String rawPath = args[0];
		ShellState state = session.getState();

		int lastSlash = rawPath.lastIndexOf('/');
		String parentPathStr;
		String newDirName;

		if (lastSlash == -1) { // just a dir name
			parentPathStr = ".";
			newDirName = rawPath;
		} else { // dir name under a path
			parentPathStr = rawPath.substring(0, lastSlash);
			newDirName = rawPath.substring(lastSlash + 1);
		}

		VirtualFile parentFile = state.getRelativeVirtualFile(parentPathStr.isEmpty() ? "." : parentPathStr);
		if (!(parentFile instanceof VirtualDirectory parentDir)) {
			return List.of("mkdir: cannot create directory '%s': No such file or directory".formatted(rawPath));
		}

		if (parentDir.getChild(newDirName) != null) {
			return List.of("mkdir: cannot create directory '%s': File exists".formatted(rawPath));
		}

		parentDir.addChild(new VirtualDirectory(newDirName));

		return List.of();
	}

	@ShellCommand(value = "touch", argUsages = "<path>")
	private List<String> commandTouch(final ShellSession session, final String commandName, final String[] args) {
		if (args.length == 0) {
			return List.of("touch: missing file operand");
		}

		List<String> output = new ArrayList<>();
		final ShellState state = session.getState();

		for (String rawPath : args) {
			int lastSlash = rawPath.lastIndexOf('/');
			String parentPathStr;
			String touchFileName;

			if (lastSlash == -1) { // just a dir name
				parentPathStr = ".";
				touchFileName = rawPath;
			} else { // dir name under a path
				parentPathStr = rawPath.substring(0, lastSlash);
				touchFileName = rawPath.substring(lastSlash + 1);
			}

			VirtualFile parentFile = state.getRelativeVirtualFile(parentPathStr.isEmpty() ? "." : parentPathStr);
			if (parentFile == null || !parentFile.isDirectory()) {
				output.add("mkdir: cannot create touch '%s': No such file or directory".formatted(rawPath));
				continue;
			}

			VirtualDirectory parentDir = (VirtualDirectory) parentFile;

			if (parentDir.getChild(touchFileName) != null) {
				// file exists: ignore
				continue;
			}

			parentDir.addChild(new VirtualRegularFile(touchFileName, ""));
		}

		return output;
	}

	@ShellCommand(value = "rm", argUsages = "[-r] <path>")
	private List<String> commandRm(final ShellSession session, final String commandName, final String[] args) {
		if (args.length == 0 || (args.length == 1 && args[0].equals("-r"))) {
			return List.of("rm: missing file operand");
		}

		List<String> output = new ArrayList<>();
		List<String> paths = new ArrayList<>();

		boolean recursiveFlag = false;
		boolean forceFlag = false;

		for (String s : args) {
			if (s.startsWith("-")) {
				if (s.contains("r")) recursiveFlag = true;
				if (s.contains("f")) forceFlag = true;
			} else {
				paths.add(s);
			}
		}

		for (String rawPath : paths) {
			VirtualFile file = session.getState().getRelativeVirtualFile(rawPath);

			if (file == null) {
				output.add("rm: cannot remove '%s': No such file or directory".formatted(rawPath));
				continue;
			}

			if (file.isDirectory() && !recursiveFlag) {
				output.add("rm: cannot remove '%s': Is a directory".formatted(rawPath));
				continue;
			}

			VirtualDirectory parent = file.getParent();
			if (parent == null && !forceFlag) {
				output.add("rm: cannot remove '%s': Permission denied".formatted(rawPath));
				continue;
			}

			file.delete();
		}

		return output;
	}

	@ShellCommand(value = "mv", argUsages = "<source> <destination>")
	private List<String> commandMv(final ShellSession session, final String commandName, final String[] args) {
		if (args.length == 0) {
			return List.of("mv: missing file operand");
		}

		if(args.length == 1) {
			return List.of("mv: missing destination file operand after '%s'".formatted(args[0]));
		}

		ShellState state = session.getState();
		List<String> output = new ArrayList<>();

		String rawSource = args[0];
		String rawDest = args[1];

		VirtualFile sourceFile = state.getRelativeVirtualFile(rawSource);
		if (sourceFile == null || !sourceFile.exists()) {
			return List.of("mv: cannot stat '%s': No such file or directory".formatted(rawSource));
		}

		VirtualFile destFile = state.getRelativeVirtualFile(rawDest);

		VirtualDirectory destDir;
		String newName;

		if (destFile != null && destFile.isDirectory()) {
			destDir = (VirtualDirectory) destFile;
			newName = sourceFile.getName();
		} else {
			int lastSlash = rawDest.lastIndexOf('/');
			String parentPath = (lastSlash == -1) ? "." : rawDest.substring(0, lastSlash);
			newName = (lastSlash == -1) ? rawDest : rawDest.substring(lastSlash + 1);

			VirtualFile parentFile = state.getRelativeVirtualFile(parentPath);
			if (parentFile == null || !parentFile.isDirectory()) {
				return List.of("mv: cannot move to '%s': No such directory".formatted(parentPath));
			}
			destDir = (VirtualDirectory) parentFile;
		}

		// check if destination already exists (linux actually doesn't do this by default, at least arch)
		if (destDir.getChild(newName) != null) {
			output.add("mv: cannot overwrite existing file '%s'".formatted(rawDest));
			return output;
		}

		VirtualDirectory sourceParent = sourceFile.getParent();
		if (sourceParent == null) {
			return List.of("mv: source has no parent directory");
		}

		if (sourceFile.isDirectory()) {
			String sourcePath = sourceFile.getPath();
			String destPath = destDir.getPath() + "/" + newName;

			if (destPath.equals(sourcePath) || destPath.startsWith(sourcePath + "/")) {
				return List.of("mv: cannot move '%s' to a subdirectory of itself, '%s'".formatted(rawSource, rawDest));
			}
		}

		sourceParent.removeChild(sourceFile.getName());

		// rename if needed
		if (!sourceFile.getName().equals(newName)) {
			// force doesn't matter here as parent will be null, although it being true isn't unrealistic to actual linux.
			sourceFile.setName(newName, true);
		}

		destDir.addChild(sourceFile);

		return List.of();
	}

	@ShellCommand({"fetch", "neofetch", "fastfetch"})
	private List<String> commandFetch(final ShellSession session, final String commandName, final String[] args) {
		String version = SpringApplication.class.getPackage().getImplementationVersion();

		String username = session.getState().getUsername();

		String hostname = "mia.ws";

		String str = String.format("""
				                  -`                     %s@%s
				                 .o+`                    ------------
				                `ooo/                    OS: (not) Arch Linux x86_64
				               `+oooo:                   Host: Inspiron 3847
				              `+oooooo:                  Kernel: Linux 6.15.6-arch1-1
				              -+oooooo+:                 Uptime: %s
				            `/:-:++oooo+:                Packages: 225 (pacman)
				           `/++++/+++++++:               Shell: miaw %s
				          `/++++++++++++++:              Terminal: /dev/pts/1
				         `/+++ooooooooooooo/`            CPU: Intel(R) Core(TM) i5-4440 (4) @ 3.30 GHz
				        ./ooosssso++osssssso+`           GPU: NVIDIA GeForce GT 625 OEM [Discrete]
				       .oossssso-````/ossssss+`          Memory: 1.82 GiB / 7.70 GiB (24%%)
				      -osssssso.      :ssssssso.         Swap: 0 B / 24.00 GiB (0%%)
				     :osssssss/        osssso+++.        Disk (/): 4.39 GiB / 203.56 GiB (2%%) - ext4
				    /ossssssss/        +ssssooo/-        Local IP (enp3s0): 192.168.0.21/24
				  `/ossssso+/:-        -:/+osssso+-      Locale: en_GB.UTF_8
				 `+sso+:-`                 `.-/+oso:
				`++:.                           `-/+/
				.`                                 `/
				""", username, hostname, uptimeService.getFormattedUptime(), version);


		return List.of(str.split("\n"));

	}

}
