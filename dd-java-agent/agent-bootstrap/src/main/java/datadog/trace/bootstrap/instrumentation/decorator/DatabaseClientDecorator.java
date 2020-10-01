package datadog.trace.bootstrap.instrumentation.decorator;

import static datadog.trace.bootstrap.instrumentation.api.Tags.DB_TYPE;

import datadog.trace.api.Config;
import datadog.trace.api.DDTags;
import datadog.trace.api.Function;
import datadog.trace.api.Functions;
import datadog.trace.api.cache.DDCache;
import datadog.trace.api.cache.DDCaches;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.Tags;

public abstract class DatabaseClientDecorator<CONNECTION> extends ClientDecorator {

  // The total number of entries in the cache will normally be less than 4, since
  // most applications only have one or two DBs, and "jdbc" itself is also used as
  // one DB_TYPE, but set the cache size to 16 to help avoid collisions.
  private static final DDCache<CharSequence, CharSequence> CACHE = DDCaches.newFixedSizeCache(16);
  private static final Function<CharSequence, CharSequence> APPEND_OPERATION =
      new Functions.Suffix(".query");

  protected abstract String dbType();

  protected abstract String dbUser(CONNECTION connection);

  protected abstract String dbInstance(CONNECTION connection);

  protected abstract String dbHostname(CONNECTION connection);

  /**
   * This should be called when the connection is being used, not when it's created.
   *
   * @param span
   * @param connection
   * @return
   */
  public AgentSpan onConnection(final AgentSpan span, final CONNECTION connection) {
    assert span != null;
    if (connection != null) {
      span.setTag(Tags.DB_USER, dbUser(connection));
      final String instanceName = dbInstance(connection);
      span.setTag(Tags.DB_INSTANCE, instanceName);

      if (instanceName != null && Config.get().isDbClientSplitByInstance()) {
        span.setTag(DDTags.SERVICE_NAME, instanceName);
      }

      String hostName = dbHostname(connection);
      if (hostName != null) {
        span.setTag(Tags.PEER_HOSTNAME, dbHostname(connection));
      }
    }
    return span;
  }

  public AgentSpan onStatement(final AgentSpan span, final CharSequence statement) {
    assert span != null;
    span.setTag(Tags.DB_STATEMENT, statement);
    return span;
  }

  protected void processDatabaseType(AgentSpan span, String dbType) {
    span.setServiceName(dbType);
    span.setOperationName(CACHE.computeIfAbsent(dbType, APPEND_OPERATION));
    span.setTag(DB_TYPE, dbType);
  }
}
