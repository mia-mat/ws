package ws.mia.service;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProfileService {

	private final Environment env;

	public ProfileService(Environment env) {
		this.env = env;
	}

	// default profiles is always "prod", so technically always in "prod", so just check for the dev flag.

	public boolean isDev() {
		return List.of(env.getActiveProfiles()).contains("dev");
	}

	public boolean isProd() {
		return !isDev();
	}
}
