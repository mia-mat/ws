package ws.mia.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.thymeleaf.util.ArrayUtils;

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

	private String lastLoginAddress = "192.168.0.1"; // this will be a cloudflare ip, security is fine :p

	public void login(HttpServletRequest request) {
		lastLoginTimestamp = System.currentTimeMillis();

		// if proxied through cloudflare, request should have these proxies.
		// just generate a fake random one otherwise to not accidentally expose an IP.
		if (request.getHeader("CF-Connecting-IP") != null
				|| request.getHeader("X-Forwarded-For") != null
				|| request.getHeader("X-Real-IP") != null) {
			Random random = new Random();
			// subnets higher than 16 look kinda weird
			lastLoginAddress = "192.168." + random.nextInt(17) + "." + random.nextInt(256);
		} else {
			lastLoginAddress = request.getRemoteAddr();
		}

	}

	public List<String> getMOTD() {
		String formattedTime = Instant.ofEpochMilli(lastLoginTimestamp).atZone(ZoneId.systemDefault()).format(MOTD_DATE_FORMATTER);

		return List.of("Last Login: " + formattedTime + " from " + lastLoginAddress);
	}

	// retrieve once
	private static final Map<String, Method> commandMethods;
	static {
		commandMethods = new HashMap<>();
		for (Method method : ShellService.class.getDeclaredMethods()) {
			if(method.isAnnotationPresent(ShellCommand.class)
			&& Arrays.equals(method.getParameterTypes(), new Class[]{String.class, String[].class})) {
				ShellCommand commandAnnotation = method.getAnnotation(ShellCommand.class);
				commandMethods.put(commandAnnotation.value(), method);
			}
		}
	}

	@SuppressWarnings("unchecked")
	public List<String> executeCommand(final String command) {
		String[] args = command.split(" ");
		for(int i = 0; i < args.length; i++) {
			args[i] = args[i].replaceFirst("^\\s+", "").trim(); // remove whitespace
		}

		if(!commandMethods.containsKey(args[0])) {
			return List.of("-bash: " + args[0] + ": command not found");
		}

		try {
			return (List<String>) commandMethods.get(args[0]).invoke(this, args[0], Arrays.copyOfRange(args, 1, args.length));
		} catch (Exception e) {
			return List.of("error while executing command: " + args[0] + ": " + e.getMessage());
		}

	}

	@Target(ElementType.METHOD)
	@Retention(RetentionPolicy.RUNTIME)
	@interface ShellCommand {
		String value(); // name
	}

	@ShellCommand("echo")
	private List<String> commandEcho(final String commandName, final String[] args) {
		String joinedArgs = String.join(" ", args);
		return List.of(joinedArgs.split("\n"));
	}

	// have a fake neofetch/fastfetch
	// exit to just stop input
	// also ofc the basics:
	// cat, ls, cd...
	// just give no perms for most things with a message for that.


}
