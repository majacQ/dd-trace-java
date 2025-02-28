package datadog.trace.instrumentation.synapse3;

import static datadog.trace.api.cache.RadixTreeCache.UNSET_PORT;
import static datadog.trace.api.cache.RadixTreeCache.UNSET_STATUS;

import datadog.trace.bootstrap.instrumentation.api.DefaultURIDataAdapter;
import datadog.trace.bootstrap.instrumentation.api.URIDataAdapter;
import datadog.trace.bootstrap.instrumentation.api.UTF8BytesString;
import datadog.trace.bootstrap.instrumentation.decorator.HttpServerDecorator;
import java.net.URI;
import org.apache.http.HttpInetConnection;
import org.apache.http.HttpResponse;
import org.apache.http.nio.NHttpServerConnection;
import org.apache.synapse.transport.passthru.SourceRequest;

public final class SynapseServerDecorator
    extends HttpServerDecorator<SourceRequest, NHttpServerConnection, HttpResponse> {
  public static final SynapseServerDecorator DECORATE = new SynapseServerDecorator();

  public static final CharSequence SYNAPSE_REQUEST = UTF8BytesString.create("http.request");
  public static final CharSequence SYNAPSE_SERVER = UTF8BytesString.create("synapse-server");
  public static final String SYNAPSE_SPAN_KEY = "dd.trace.synapse.span";

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"synapse3"};
  }

  @Override
  protected CharSequence component() {
    return SYNAPSE_SERVER;
  }

  @Override
  protected String method(final SourceRequest request) {
    return request.getMethod();
  }

  @Override
  protected URIDataAdapter url(final SourceRequest request) {
    return new DefaultURIDataAdapter(URI.create(request.getUri()));
  }

  @Override
  protected String peerHostIP(final NHttpServerConnection connection) {
    if (connection instanceof HttpInetConnection) {
      return ((HttpInetConnection) connection).getRemoteAddress().getHostAddress();
    }
    return null;
  }

  @Override
  protected int peerPort(final NHttpServerConnection connection) {
    if (connection instanceof HttpInetConnection) {
      return ((HttpInetConnection) connection).getRemotePort();
    }
    return UNSET_PORT;
  }

  @Override
  protected int status(final HttpResponse response) {
    if (null != response.getStatusLine()) {
      return response.getStatusLine().getStatusCode();
    }
    return UNSET_STATUS;
  }
}
