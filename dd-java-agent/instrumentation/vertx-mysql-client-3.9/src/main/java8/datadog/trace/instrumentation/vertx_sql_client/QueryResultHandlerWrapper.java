package datadog.trace.instrumentation.vertx_sql_client;

import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.context.TraceScope;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.sqlclient.SqlResult;

public class QueryResultHandlerWrapper<T, R extends SqlResult<T>>
    implements Handler<AsyncResult<R>> {
  private final Handler<AsyncResult<R>> handler;
  private final AgentSpan span;
  private final TraceScope.Continuation continuation;

  public QueryResultHandlerWrapper(
      final Handler<AsyncResult<R>> handler,
      final AgentSpan span,
      final TraceScope.Continuation continuation) {
    this.handler = handler;
    this.span = span;
    this.continuation = continuation;
  }

  @Override
  public void handle(final AsyncResult<R> event) {
    TraceScope scope = null;
    try {
      if (null != span) {
        span.finish();
      }
      if (null != continuation) {
        scope = continuation.activate();
      }
      handler.handle(event);
    } finally {
      if (null != scope) {
        scope.close();
      }
    }
  }
}
