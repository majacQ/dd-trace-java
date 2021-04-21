package datadog.trace.instrumentation.jbosslogmanager;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.hasClassNamed;
import static datadog.trace.agent.tooling.bytebuddy.matcher.DDElementMatchers.extendsClass;
import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.log.UnionMap;
import datadog.trace.api.Config;
import datadog.trace.api.CorrelationIdentifier;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.Tags;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import org.jboss.logmanager.ExtLogRecord;

@AutoService(Instrumenter.class)
public class ExtLogRecordInstrumentation extends Instrumenter.Tracing {
  public ExtLogRecordInstrumentation() {
    super("jboss-logmanager");
  }

  @Override
  protected boolean defaultEnabled() {
    return Config.get().isLogsInjectionEnabled();
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return hasClassNamed("org.jboss.logmanager.ExtLogRecord");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return extendsClass(named("org.jboss.logmanager.ExtLogRecord"));
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("org.jboss.logmanager.ExtLogRecord", AgentSpan.Context.class.getName());
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod().and(named("getMdc")).and(takesArgument(0, String.class)),
        ExtLogRecordInstrumentation.class.getName() + "$GetMdcAdvice");

    transformers.put(
        isMethod().and(named("getMdcCopy")).and(takesArguments(0)),
        ExtLogRecordInstrumentation.class.getName() + "$GetMdcCopyAdvice");

    return transformers;
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.agent.tooling.log.UnionMap",
      "datadog.trace.agent.tooling.log.UnionMap$ConcatenatedSet",
      "datadog.trace.agent.tooling.log.UnionMap$ConcatenatedSet$ConcatenatedSetIterator",
    };
  }

  public static class GetMdcAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void getMdcValue(
        @Advice.This ExtLogRecord record,
        @Advice.Argument(0) String key,
        @Advice.Return(readOnly = false) String value) {

      // if the mdc had a value for the key, or the key is null (invalid for a switch)
      // just return
      if (value != null || key == null) {
        return;
      }

      switch (key) {
        case Tags.DD_SERVICE:
          value = Config.get().getServiceName();
          return;
        case Tags.DD_ENV:
          value = Config.get().getEnv();
          return;
        case Tags.DD_VERSION:
          value = Config.get().getVersion();
          return;
        case "dd.trace_id":
          {
            AgentSpan.Context context =
                InstrumentationContext.get(ExtLogRecord.class, AgentSpan.Context.class).get(record);
            if (context != null) {
              value = context.getTraceId().toString();
            }
            return;
          }
        case "dd.span_id":
          {
            AgentSpan.Context context =
                InstrumentationContext.get(ExtLogRecord.class, AgentSpan.Context.class).get(record);
            if (context != null) {
              value = context.getSpanId().toString();
            }

            return;
          }
      }
    }
  }

  public static class GetMdcCopyAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This ExtLogRecord record,
        @Advice.Return(typing = Assigner.Typing.DYNAMIC, readOnly = false)
            Map<String, String> mdc) {

      if (mdc instanceof UnionMap) {
        return;
      }

      AgentSpan.Context context =
          InstrumentationContext.get(ExtLogRecord.class, AgentSpan.Context.class).get(record);
      boolean mdcTagsInjectionEnabled = Config.get().isLogsMDCTagsInjectionEnabled();

      // Nothing to add so return early
      if (context == null && !mdcTagsInjectionEnabled) {
        return;
      }

      Map<String, String> correlationValues = new HashMap<>();

      if (context != null) {
        correlationValues.put(
            CorrelationIdentifier.getTraceIdKey(), context.getTraceId().toString());
        correlationValues.put(CorrelationIdentifier.getSpanIdKey(), context.getSpanId().toString());
      }

      if (mdcTagsInjectionEnabled) {
        correlationValues.put(Tags.DD_SERVICE, Config.get().getServiceName());
        correlationValues.put(Tags.DD_ENV, Config.get().getEnv());
        correlationValues.put(Tags.DD_VERSION, Config.get().getVersion());
      }

      if (mdc == null) {
        mdc = correlationValues;
      } else {
        mdc = new UnionMap<>(mdc, correlationValues);
      }
    }
  }
}
