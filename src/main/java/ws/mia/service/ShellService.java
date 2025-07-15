package ws.mia.service;

import org.springframework.stereotype.Service;
import ws.mia.SpringApplication;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ShellService {

	DateTimeFormatter MOTD_DATE_FORMATTER = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss yyyy", Locale.ENGLISH);

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
					&& Arrays.equals(method.getParameterTypes(), new Class[]{String.class, String[].class})) {
				methods.add(method);
			}
		}
		return methods;
	}

	public static class CommandResponse {
		private final List<String> lines;
		private final boolean allowFutureInput;

		public CommandResponse(List<String> lines, boolean allowFutureInput) {
			this.lines = lines;
			this.allowFutureInput = allowFutureInput;
		}

		public CommandResponse(List<String> lines) {
			this(lines, true);
		}

		public List<String> getLines() {
			return lines;
		}

		public boolean getAllowFutureInput() {
			return allowFutureInput;
		}
	}

	public CommandResponse executeCommand(final String command) {
		String[] args = command.split(" ");
		for (int i = 0; i < args.length; i++) {
			args[i] = args[i].replaceFirst("^\\s+", "").trim(); // remove whitespace
		}

		if (!commandMethods.containsKey(args[0])) {
			return new CommandResponse(List.of("-bash: " + args[0] + ": command not found"));
		}

		try {
			return (CommandResponse) commandMethods.get(args[0]).invoke(this, args[0], Arrays.copyOfRange(args, 1, args.length));
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
	private CommandResponse commandEcho(final String commandName, final String[] args) {
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
		return new CommandResponse(output);
	}

	@ShellCommand("exit")
	private CommandResponse commandExit(final String commandName, final String[] args) {
		return new CommandResponse(List.of("logout", "Connection to mia.ws closed."), false);
	}

	@ShellCommand("help")
	private CommandResponse commandHelp(final String commandName, final String[] args) {
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

		return new CommandResponse(responseList);
	}



	// have a fake neofetch/fastfetch
	// also ofc the basics:
	// cat, ls, cd...
	// just give no perms for most things with a message for that.


}
