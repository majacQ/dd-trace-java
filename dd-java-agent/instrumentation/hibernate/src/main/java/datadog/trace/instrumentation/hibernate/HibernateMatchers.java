package datadog.trace.instrumentation.hibernate;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.hasClassNamed;

import net.bytebuddy.matcher.ElementMatcher;

public final class HibernateMatchers {

  public static final ElementMatcher<ClassLoader> CLASS_LOADER_MATCHER =
      hasClassNamed("org.hibernate.Session");

  private HibernateMatchers() {}
}
