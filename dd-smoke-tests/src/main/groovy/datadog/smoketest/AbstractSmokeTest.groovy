package datadog.smoketest

import datadog.trace.agent.test.server.http.TestHttpServer
import datadog.trace.agent.test.utils.PortUtils
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

import static datadog.trace.agent.test.server.http.TestHttpServer.httpServer
import static datadog.trace.test.util.ForkedTestUtils.getMaxMemoryArgumentForFork
import static datadog.trace.test.util.ForkedTestUtils.getMinMemoryArgumentForFork

abstract class AbstractSmokeTest extends Specification {

  public static final PROFILING_START_DELAY_SECONDS = 1
  public static final int PROFILING_RECORDING_UPLOAD_PERIOD_SECONDS = 5
  public static final String SERVICE_NAME = "smoke-test-java-app"
  public static final String ENV = "smoketest"
  public static final String VERSION = "99"

  @Shared
  protected String workingDirectory = System.getProperty("user.dir")
  @Shared
  protected String buildDirectory = System.getProperty("datadog.smoketest.builddir")
  @Shared
  protected String shadowJarPath = System.getProperty("datadog.smoketest.agent.shadowJar.path")
  @Shared
  protected static int profilingPort = -1

  @Shared
  protected String[] defaultJavaProperties

  @Shared
  protected Process testedProcess

  @Shared
  protected BlockingQueue<TestHttpServer.HandlerApi.RequestApi> traceRequests = new LinkedBlockingQueue<>()

  @Shared
  protected AtomicInteger traceCount = new AtomicInteger()

  /**
   * Will be initialized after calling {@linkplain AbstractSmokeTest#checkLog} and hold {@literal true}
   * if there are any ERROR or WARN lines in the test application log.
   */
  @Shared
  def logHasErrors

  @Shared
  def logFilePath = "${buildDirectory}/reports/testProcess.${this.getClass().getName()}.log"

  @Shared
  @AutoCleanup
  protected TestHttpServer server = httpServer {
    handlers {
      prefix("/v0.4/traces") {
        def countString = request.getHeader("X-Datadog-Trace-Count")
        int count = countString != null ? Integer.parseInt(countString) : 0
        traceCount.addAndGet(count)
        println("Received traces: " + countString)
        traceRequests.add(request)
        response.status(200).send()
      }
    }
  }

  def setup() {
    // TODO: once java7 support is dropped use testedProcess.isAlive() instead
    try {
      testedProcess.exitValue()
      assert false: "Process not alive before test"
    } catch (IllegalThreadStateException ignored) {
      // expected
    }

    traceRequests.clear()
    traceCount.set(0)
  }

  def setupSpec() {
    if (buildDirectory == null || shadowJarPath == null) {
      throw new AssertionError("Expected system properties not found. Smoke tests have to be run from Gradle. Please make sure that is the case.")
    }
    assert Files.isDirectory(Paths.get(buildDirectory))
    assert Files.isRegularFile(Paths.get(shadowJarPath))

    startServer()
    System.out.println("Mock agent started at " + server.address)

    defaultJavaProperties = [
      "${getMaxMemoryArgumentForFork()}",
      "${getMinMemoryArgumentForFork()}",
      "-javaagent:${shadowJarPath}",
      "-XX:ErrorFile=/tmp/hs_err_pid%p.log",
      "-Ddd.trace.agent.port=${server.address.port}",
      "-Ddd.service.name=${SERVICE_NAME}",
      "-Ddd.env=${ENV}",
      "-Ddd.version=${VERSION}",
      "-Ddd.profiling.enabled=true",
      "-Ddd.profiling.start-delay=${PROFILING_START_DELAY_SECONDS}",
      "-Ddd.profiling.upload.period=${PROFILING_RECORDING_UPLOAD_PERIOD_SECONDS}",
      "-Ddd.profiling.url=${getProfilingUrl()}",
      "-Ddatadog.slf4j.simpleLogger.defaultLogLevel=debug",
      "-Dorg.slf4j.simpleLogger.defaultLogLevel=debug"
    ]

    ProcessBuilder processBuilder = createProcessBuilder()

    processBuilder.environment().put("JAVA_HOME", System.getProperty("java.home"))
    processBuilder.environment().put("DD_API_KEY", apiKey())

    processBuilder.redirectErrorStream(true)
    processBuilder.redirectOutput(ProcessBuilder.Redirect.to(new File(logFilePath)))

    testedProcess = processBuilder.start()
  }

  String javaPath() {
    final String separator = System.getProperty("file.separator")
    return System.getProperty("java.home") + separator + "bin" + separator + "java"
  }

  def cleanupSpec() {
    int maxAttempts = 10
    Integer exitValue
    for (int attempt = 1; attempt <= maxAttempts != null; attempt++) {
      try {
        exitValue = testedProcess?.exitValue()
        break
      }
      catch (Throwable e) {
        if (attempt == 1) {
          System.out.println("Destroying instrumented process")
          testedProcess.destroy()
        }
        if (attempt == maxAttempts - 1) {
          System.out.println("Destroying instrumented process (forced)")
          testedProcess.destroyForcibly()
        }
        sleep 1_000
      }
    }

    stopServer()

    if (exitValue != null) {
      System.out.println("Instrumented process exited with " + exitValue)
    } else if (testedProcess != null) {
      throw new TimeoutException("Instrumented process failed to exit")
    }
  }

  def getProfilingUrl() {
    if (profilingPort == -1) {
      profilingPort = PortUtils.randomOpenPort()
    }
    return "http://localhost:${profilingPort}/"
  }

  def startServer() {
    server.start()
  }

  def stopServer() {
    // do nothing; 'server' is autocleanup
  }

  /**
   * Check the test application log and set {@linkplain AbstractSmokeTest#logHasErrors} variable
   * @param checker custom closure to run on each log line
   */
  def checkLog(Closure checker) {
    new File(logFilePath).eachLine {
      if (it.contains("ERROR") || it.contains("ASSERTION FAILED")) {
        println it
        logHasErrors = true
      }
      checker(it)
    }
    if (logHasErrors) {
      println "Test application log is containing errors. See full run logs in ${logFilePath}"
    }
  }

  def checkLog() {
    checkLog {}
  }

  abstract ProcessBuilder createProcessBuilder()

  String apiKey() {
    return "01234567890abcdef123456789ABCDEF"
  }

  int waitForTraceCount(int count) {
    long start = System.nanoTime()
    long timeout = TimeUnit.SECONDS.toNanos(10)
    int current = traceCount.get()
    while (current < count) {
      if (System.nanoTime() - start >= timeout) {
        throw new TimeoutException("Timed out waiting for " + count + " traces. Have only received " + current + ".")
      }
      Thread.sleep(500)
      current = traceCount.get()
    }
    return current
  }
}
