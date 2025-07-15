package ws.mia.app;

import com.googlecode.htmlcompressor.compressor.HtmlCompressor;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.PrintWriter;


@Profile("prod")
@Component
public class HtmlMinifyFilter implements Filter {
	protected FilterConfig config;

	public void init(FilterConfig config) {
		this.config = config;
	}

	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws ServletException, IOException {
		ServletResponse newResponse = response;

		if (request instanceof HttpServletRequest) {
			newResponse = new CharResponseWrapper((HttpServletResponse) response);
		}

		chain.doFilter(request, newResponse);

		if (newResponse instanceof CharResponseWrapper) {
			String text = newResponse.toString();
			if (text != null) {
				HtmlCompressor htmlCompressor = new HtmlCompressor();
				response.getWriter().write(htmlCompressor.compress(text));
			}
		}
	}

	static class CharResponseWrapper extends HttpServletResponseWrapper {
		protected CharArrayWriter charWriter;
		protected PrintWriter writer;
		protected boolean getOutputStreamCalled;
		protected boolean getWriterCalled;

		public CharResponseWrapper(HttpServletResponse response) {
			super(response);

			charWriter = new CharArrayWriter();
		}

		public ServletOutputStream getOutputStream() throws IOException {
			if (getWriterCalled) {
				throw new IllegalStateException("getWriter already called");
			}

			getOutputStreamCalled = true;
			return super.getOutputStream();
		}

		public PrintWriter getWriter() {
			if (writer != null) {
				return writer;
			}
			if (getOutputStreamCalled) {
				throw new IllegalStateException("getOutputStream already called");
			}
			getWriterCalled = true;
			writer = new PrintWriter(charWriter);
			return writer;
		}

		public String toString() {
			String s = null;

			if (writer != null) {
				s = charWriter.toString();
			}
			return s;
		}
	}
}