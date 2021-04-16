import io.vertx.core.AsyncResult
import io.vertx.core.Handler
import io.vertx.redis.client.Command
import io.vertx.redis.client.Request
import io.vertx.redis.client.Response

class VertxRedisForkedTest extends VertxRedisTestBase {

  def "set and get command"() {
    when:
    def set = runWithParentAndHandler({ Handler<AsyncResult<Response>> h ->
      redis.send(Request.cmd(Command.SET).arg("foo").arg("bar"), h)
    }, this.&responseToString)
    def get = runWithParentAndHandler({ Handler<AsyncResult<Response>> h ->
      redis.send(Request.cmd(Command.GET).arg("foo"), h)
    }, this.&responseToString)

    then:
    set == "OK"
    get == "bar"
    assertTraces(2) {
      parentTraceWithCommandAndHandler(it, "SET")
      parentTraceWithCommandAndHandler(it, "GET")
    }
  }
}
