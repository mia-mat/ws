package ws.mia.website.battlecode;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/// Serves the dashboard at "/" for the hosts in `battlecode.hosts`, leaving every other host untouched.
///
/// Phoenix sets Host to the container's address and passes the original host in X-Forwarded-Host,
/// so that header is checked first; Host is the fallback for local runs (e.g. battlecode.localhost:8080).
@Component
public class BattlecodeHostFilter extends OncePerRequestFilter {

    private final Set<String> hosts;

    public BattlecodeHostFilter(@Value("${battlecode.hosts:battlecode.mia.ws}") String hosts) {
        this.hosts = Arrays.stream(hosts.split("[,\\s]+"))
                .map(host -> host.strip().toLowerCase(Locale.ROOT))
                .filter(host -> !host.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("/".equals(request.getRequestURI()) && hosts.contains(requestHost(request))) {
            request.getRequestDispatcher("/battlecode/").forward(request, response);
            return;
        }
        chain.doFilter(request, response);
    }

    /// The host the visitor asked for, lower-cased and without a port.
    static String requestHost(HttpServletRequest request) {
        String host = request.getHeader("X-Forwarded-Host");
        if (host == null || host.isBlank()) host = request.getHeader("Host");
        if (host == null) return "";

        host = host.split(",")[0].strip().toLowerCase(Locale.ROOT); // first hop if a proxy appended more
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(']') < colon) host = host.substring(0, colon);
        return host;
    }

}
