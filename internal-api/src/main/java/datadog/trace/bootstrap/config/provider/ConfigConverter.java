package datadog.trace.bootstrap.config.provider;

import de.thetaphi.forbiddenapis.SuppressForbidden;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ConfigConverter {

  private static final Logger log = LoggerFactory.getLogger(ConfigConverter.class);

  private static final Pattern COMMA_SEPARATED =
      Pattern.compile("(([^,:]+:[^,:]*,)*([^,:]+:[^,:]*),?)?");
  private static final Pattern SPACE_SEPARATED = Pattern.compile("((\\S+:\\S*)\\s+)*(\\S+:\\S*)?");
  private static final Pattern ILLEGAL_SPACE_SEPARATED = Pattern.compile("(:\\S+:)+");

  private static final ValueOfLookup LOOKUP = new ValueOfLookup();

  /**
   * @param value to parse by tClass::valueOf
   * @param tClass should contain static parsing method "T valueOf(String)"
   * @param <T>
   * @return value == null || value.trim().isEmpty() ? defaultValue : tClass.valueOf(value)
   * @throws NumberFormatException
   */
  static <T> T valueOf(final String value, @Nonnull final Class<T> tClass) {
    Objects.requireNonNull(tClass, "tClass is marked non-null but is null");
    if (value == null || value.trim().isEmpty()) {
      return null;
    }
    try {
      return (T) LOOKUP.get(tClass).invoke(value);
    } catch (final NumberFormatException e) {
      throw e;
    } catch (final Throwable e) {
      log.debug("Can't parse: ", e);
      throw new NumberFormatException(e.toString());
    }
  }

  @Nonnull
  static List<String> parseList(final String str) {
    return parseList(str, ",");
  }

  @Nonnull
  @SuppressForbidden
  static List<String> parseList(final String str, final String separator) {
    if (str == null || str.trim().isEmpty()) {
      return Collections.emptyList();
    }

    final String[] tokens = str.split(separator, -1);
    // Remove whitespace from each item.
    for (int i = 0; i < tokens.length; i++) {
      tokens[i] = tokens[i].trim();
    }
    return Collections.unmodifiableList(Arrays.asList(tokens));
  }

  @Nonnull
  static Map<String, String> parseMap(final String str, final String settingName) {
    // If we ever want to have default values besides an empty map, this will need to change.
    if (str == null) {
      return Collections.emptyMap();
    }
    String trimmed = str.trim();
    if (trimmed.isEmpty()) {
      return Collections.emptyMap();
    }
    if (COMMA_SEPARATED.matcher(trimmed).matches()) {
      return parseMap(str, settingName, ",");
    }
    if (SPACE_SEPARATED.matcher(trimmed).matches()
        && !ILLEGAL_SPACE_SEPARATED.matcher(trimmed).find()) {
      return parseMap(str, settingName, "\\s+");
    }
    log.warn(
        "Invalid config for {}: '{}'. Must match 'key1:value1,key2:value2' or 'key1:value1 key2:value2'.",
        settingName,
        str);
    return Collections.emptyMap();
  }

  @SuppressForbidden
  private static Map<String, String> parseMap(
      final String str, final String settingName, final String separator) {
    final String[] tokens = str.split(separator);
    final Map<String, String> map = newHashMap(tokens.length);

    for (final String token : tokens) {
      final String[] keyValue = token.split(":", 2);
      if (keyValue.length == 2) {
        final String key = keyValue[0].trim();
        final String value = keyValue[1].trim();
        if (value.length() <= 0) {
          log.warn("Ignoring empty value for key '{}' in config for {}", key, settingName);
          continue;
        }
        map.put(key, value);
      }
    }
    return Collections.unmodifiableMap(map);
  }

  @Nonnull
  private static Map<String, String> newHashMap(final int size) {
    return new HashMap<>(size + 1, 1f);
  }

  @Nonnull
  @SuppressForbidden
  static BitSet parseIntegerRangeSet(@Nonnull String str, final String settingName)
      throws NumberFormatException {
    str = str.replaceAll("\\s", "");
    if (!str.matches("\\d{3}(?:-\\d{3})?(?:,\\d{3}(?:-\\d{3})?)*")) {
      log.warn(
          "Invalid config for {}: '{}'. Must be formatted like '400-403,405,410-499'.",
          settingName,
          str);
      throw new NumberFormatException();
    }

    final int lastSeparator = Math.max(str.lastIndexOf(','), str.lastIndexOf('-'));
    final int maxValue = Integer.parseInt(str.substring(lastSeparator + 1));
    final BitSet set = new BitSet(maxValue);
    final String[] tokens = str.split(",", -1);
    for (final String token : tokens) {
      final int separator = token.indexOf('-');
      if (separator == -1) {
        set.set(Integer.parseInt(token));
      } else if (separator > 0) {
        final int left = Integer.parseInt(token.substring(0, separator));
        final int right = Integer.parseInt(token.substring(separator + 1));
        final int min = Math.min(left, right);
        final int max = Math.max(left, right);
        set.set(min, max + 1);
      }
    }
    return set;
  }

  private static class ValueOfLookup extends ClassValue<MethodHandle> {
    private static final MethodHandles.Lookup PUBLIC_LOOKUP = MethodHandles.publicLookup();

    @Override
    protected MethodHandle computeValue(Class<?> type) {
      try {
        return PUBLIC_LOOKUP.findStatic(type, "valueOf", MethodType.methodType(type, String.class));
      } catch (final NoSuchMethodException | IllegalAccessException e) {
        log.debug("Can't invoke or access 'valueOf': ", e);
        throw new RuntimeException(e);
      }
    }
  }
}
