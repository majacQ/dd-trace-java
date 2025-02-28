package datadog.trace.common.metrics;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

import datadog.trace.api.WellKnownTags;
import datadog.trace.core.serialization.GrowableBuffer;
import datadog.trace.core.serialization.WritableFormatter;
import datadog.trace.core.serialization.msgpack.MsgPackWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SerializingMetricWriter implements MetricWriter {

  private static final Logger log = LoggerFactory.getLogger(SerializingMetricWriter.class);

  private static final byte[] HOSTNAME = "Hostname".getBytes(ISO_8859_1);
  private static final byte[] NAME = "Name".getBytes(ISO_8859_1);
  private static final byte[] ENV = "Env".getBytes(ISO_8859_1);
  private static final byte[] SERVICE = "Service".getBytes(ISO_8859_1);
  private static final byte[] RESOURCE = "Resource".getBytes(ISO_8859_1);
  private static final byte[] VERSION = "Version".getBytes(ISO_8859_1);
  private static final byte[] HITS = "Hits".getBytes(ISO_8859_1);
  private static final byte[] ERRORS = "Errors".getBytes(ISO_8859_1);
  private static final byte[] TOP_LEVEL_HITS = "TopLevelHits".getBytes(ISO_8859_1);
  private static final byte[] DURATION = "Duration".getBytes(ISO_8859_1);
  private static final byte[] TYPE = "Type".getBytes(ISO_8859_1);
  private static final byte[] HTTP_STATUS_CODE = "HTTPStatusCode".getBytes(ISO_8859_1);
  private static final byte[] START = "Start".getBytes(ISO_8859_1);
  private static final byte[] STATS = "Stats".getBytes(ISO_8859_1);
  private static final byte[] OK_SUMMARY = "OkSummary".getBytes(ISO_8859_1);
  private static final byte[] ERROR_SUMMARY = "ErrorSummary".getBytes(ISO_8859_1);

  private final WellKnownTags wellKnownTags;
  private final WritableFormatter writer;
  private final Sink sink;
  private final GrowableBuffer buffer;

  public SerializingMetricWriter(WellKnownTags wellKnownTags, Sink sink) {
    this(wellKnownTags, sink, 512 * 1024);
  }

  public SerializingMetricWriter(WellKnownTags wellKnownTags, Sink sink, int initialCapacity) {
    this.wellKnownTags = wellKnownTags;
    this.buffer = new GrowableBuffer(initialCapacity);
    this.writer = new MsgPackWriter(buffer);
    this.sink = sink;
  }

  @Override
  public void startBucket(int metricCount, long start, long duration) {
    writer.startMap(4);

    writer.writeUTF8(HOSTNAME);
    writer.writeUTF8(wellKnownTags.getHostname());

    writer.writeUTF8(ENV);
    writer.writeUTF8(wellKnownTags.getEnv());

    writer.writeUTF8(VERSION);
    writer.writeUTF8(wellKnownTags.getVersion());

    writer.writeUTF8(STATS);
    writer.startArray(1);

    writer.startMap(3);

    writer.writeUTF8(START);
    writer.writeLong(start);

    writer.writeUTF8(DURATION);
    writer.writeLong(duration);

    writer.writeUTF8(STATS);
    writer.startArray(metricCount);
  }

  @Override
  public void add(MetricKey key, AggregateMetric aggregate) {

    writer.startMap(11);

    writer.writeUTF8(NAME);
    writer.writeUTF8(key.getOperationName());

    writer.writeUTF8(SERVICE);
    writer.writeUTF8(key.getService());

    writer.writeUTF8(RESOURCE);
    writer.writeUTF8(key.getResource());

    writer.writeUTF8(TYPE);
    writer.writeUTF8(key.getType());

    writer.writeUTF8(HTTP_STATUS_CODE);
    writer.writeInt(key.getHttpStatusCode());

    writer.writeUTF8(HITS);
    writer.writeInt(aggregate.getHitCount());

    writer.writeUTF8(ERRORS);
    writer.writeInt(aggregate.getErrorCount());

    writer.writeUTF8(TOP_LEVEL_HITS);
    writer.writeInt(aggregate.getTopLevelCount());

    writer.writeUTF8(DURATION);
    writer.writeLong(aggregate.getDuration());

    writer.writeUTF8(OK_SUMMARY);
    writer.writeBinary(aggregate.getOkLatencies().serialize());

    writer.writeUTF8(ERROR_SUMMARY);
    writer.writeBinary(aggregate.getErrorLatencies().serialize());
  }

  @Override
  public void finishBucket() {
    buffer.mark();
    sink.accept(buffer.messageCount(), buffer.slice());
    buffer.reset();
  }

  @Override
  public void reset() {
    buffer.reset();
  }
}
