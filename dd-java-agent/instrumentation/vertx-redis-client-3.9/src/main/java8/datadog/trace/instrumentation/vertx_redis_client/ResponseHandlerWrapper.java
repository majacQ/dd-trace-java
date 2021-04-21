package datadog.trace.instrumentation.vertx_redis_client;

import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.context.TraceScope;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.redis.client.Response;

public class ResponseHandlerWrapper implements Handler<AsyncResult<Response>> {
  private final Handler<AsyncResult<Response>> handler;
  private final AgentSpan span;
  private final TraceScope.Continuation continuation;

  public ResponseHandlerWrapper(
      final Handler<AsyncResult<Response>> handler,
      final AgentSpan span,
      final TraceScope.Continuation continuation) {
    this.handler = handler;
    this.span = span;
    this.continuation = continuation;
  }

  @Override
  public void handle(final AsyncResult<Response> event) {
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
