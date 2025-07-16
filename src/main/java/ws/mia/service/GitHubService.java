package ws.mia.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

@Service
public class GitHubService {
	private final RestTemplate restTemplate;
	private final ObjectMapper mapper;

	private static final String GITHUB_USER_ID = System.getenv("GITHUB_USER_ID");
	private static final String GITHUB_TOKEN = System.getenv("GITHUB_TOKEN");

	private String GITHUB_USERNAME = null;

	private long cacheTimeout;
	private long lastCacheTime;

	// key value is the repository's GitHub ID
	private Collection<Repository> publicRepoCache;


	public GitHubService() {
		this.restTemplate = new RestTemplate();

		if (GITHUB_TOKEN != null) {
			this.restTemplate.getInterceptors().add((request, body, execution) -> {
				request.getHeaders().setBearerAuth(GITHUB_TOKEN);
				return execution.execute(request, body);
			});
		}

		this.mapper = new ObjectMapper();

		this.cacheTimeout = 1000*60*60*6; // 6 hours default

		this.publicRepoCache = new HashSet<>();

		GITHUB_USERNAME = fetchUsername(GITHUB_USER_ID);
	}


	private String fetchUsername(String id) {
		try {
			String url = "https://api.github.com/user/" + id;

			HttpClient client = HttpClient.newHttpClient();

			HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
					.uri(new URI(url))
					.GET();

			if (GITHUB_TOKEN != null) {
				requestBuilder.header("Authorization", "Bearer " + GITHUB_TOKEN);
			}

			HttpRequest request = requestBuilder.build();

			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			JsonNode jsonResponse = mapper.readTree(response.body());

			String username = jsonResponse.get("login").asText();

			if (username != null) {
				client.close();
				return username;
			}

			client.close();
			throw new IOException("Login field not found in response " + jsonResponse.toString());

		} catch (IOException | URISyntaxException | InterruptedException e) {
			throw new RuntimeException("Could not fetch github username from id %s".formatted(GITHUB_USER_ID), e);
		}


	}

	public void refetchUsername() {
		this.GITHUB_USERNAME = fetchUsername(GITHUB_USER_ID);
	}

	public void cloneRepo(String repoName, File parent) {
		if(!parent.isDirectory()) return;
		// todo
	}

	private Collection<Repository> fetchPublicRepositories() {
		if(GITHUB_USERNAME == null) return new ArrayList<>();

		String url = "https://api.github.com/users/" + GITHUB_USERNAME + "/repos";
		ResponseEntity<Repository[]> response = restTemplate.getForEntity(url, Repository[].class);
		Repository[] body = response.getBody();

		if(body == null) {
			return publicRepoCache; // fall back on old data
		}

		this.lastCacheTime = System.currentTimeMillis();
		this.publicRepoCache = Arrays.asList(body);
		return this.publicRepoCache;

	}

	// uses cached repositories if available
	public Collection<Repository> getOrFetchPublicRepositories() {
		if(GITHUB_USERNAME == null) return new ArrayList<>();
		if(System.currentTimeMillis() < lastCacheTime + cacheTimeout || publicRepoCache.isEmpty()) {
			publicRepoCache = fetchPublicRepositories();
		}
		return Collections.unmodifiableCollection(publicRepoCache);

	}

	public void setCacheTimeout(long millis) {
		if(millis > 0) cacheTimeout = millis;
	}

	public long getCacheTimeout() {
		return cacheTimeout;
	}

	public UsernamePasswordCredentialsProvider getCredentials() {
		if(GITHUB_TOKEN == null) return null;
		if(GITHUB_USERNAME == null) return null;
		return new UsernamePasswordCredentialsProvider(GITHUB_USERNAME, GITHUB_TOKEN);
	}

	public static class Repository {
		private long id;

		private String name;

		@JsonProperty("html_url")
		private String url;

		private String description;

		@JsonProperty("clone_url")
		private String cloneUrl;

		@JsonProperty("default_branch")
		private String defaultBranch;

		@Override
		public String toString() {
			return "Repository{" +
					"id=" + id +
					", name='" + name + '\'' +
					", url='" + url + '\'' +
					", description='" + description + '\'' +
					", cloneUrl='" + cloneUrl + '\'' +
					", defaultBranch='" + defaultBranch + '\'' +
					'}';
		}

		public Repository(long id, String name, String url, String description, String cloneUrl, String defaultBranch) {
			this.id = id;
			this.name = name;
			this.url = url;
			this.description = description;
			this.cloneUrl = cloneUrl;
			this.defaultBranch = defaultBranch;
		}

		public long getId() {
			return id;
		}

		public String getName() {
			return name;
		}

		public String getUrl() {
			return url;
		}

		public String getDescription() {
			return description;
		}

		public void setId(long id) {
			this.id = id;
		}

		public void setName(String name) {
			this.name = name;
		}

		public void setUrl(String url) {
			this.url = url;
		}

		public void setDescription(String description) {
			this.description = description;
		}

		public String getCloneUrl() {
			return cloneUrl;
		}

		public void setCloneUrl(String cloneUrl) {
			this.cloneUrl = cloneUrl;
		}

		public String getDefaultBranch() {
			return defaultBranch;
		}

		public void setDefaultBranch(String defaultBranch) {
			this.defaultBranch = defaultBranch;
		}
	}

}
