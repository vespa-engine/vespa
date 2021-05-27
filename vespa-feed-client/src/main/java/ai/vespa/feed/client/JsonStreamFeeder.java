// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import ai.vespa.feed.client.FeedClient.OperationType;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static ai.vespa.feed.client.FeedClient.OperationType.put;
import static ai.vespa.feed.client.FeedClient.OperationType.remove;
import static ai.vespa.feed.client.FeedClient.OperationType.update;
import static com.fasterxml.jackson.core.JsonToken.START_OBJECT;
import static com.fasterxml.jackson.core.JsonToken.VALUE_FALSE;
import static com.fasterxml.jackson.core.JsonToken.VALUE_STRING;
import static com.fasterxml.jackson.core.JsonToken.VALUE_TRUE;
import static java.lang.Math.min;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

/**
 * @author jonmv
 */
public class JsonStreamFeeder implements Closeable {

    private final FeedClient client;
    private final OperationParameters protoParameters;

    private JsonStreamFeeder(FeedClient client, OperationParameters protoParameters) {
        this.client = client;
        this.protoParameters = protoParameters;
    }

    public static Builder builder(FeedClient client) { return new Builder(client); }

    /** Feeds a stream containing a JSON array of feed operations on the form
     * <pre>
     *     [
     *       {
     *         "id": "id:ns:type::boo",
     *         "fields": { ... document fields ... }
     *       },
     *       {
     *         "put": "id:ns:type::foo",
     *         "fields": { ... document fields ... }
     *       },
     *       {
     *         "update": "id:ns:type:n=4:bar",
     *         "create": true,
     *         "fields": { ... partial update fields ... }
     *       },
     *       {
     *         "remove": "id:ns:type:g=foo:bar",
     *         "condition": "type.baz = \"bax\""
     *       },
     *       ...
     *     ]
     * </pre>
     * Note that {@code "id"} is an alias for the document put operation.
     */
    public void feed(InputStream jsonStream) throws IOException {
        feed(jsonStream, 1 << 26, false);
    }

    BenchmarkResult benchmark(InputStream jsonStream) throws IOException {
        return feed(jsonStream, 1 << 26, true).get();
    }

    Optional<BenchmarkResult> feed(InputStream jsonStream, int size, boolean benchmark) throws IOException {
        RingBufferStream buffer = new RingBufferStream(jsonStream, size);
        buffer.expect(JsonToken.START_ARRAY);
        AtomicInteger okCount = new AtomicInteger();
        AtomicInteger failedCount = new AtomicInteger();
        long startTime = System.nanoTime();
        CompletableFuture<Result> result;
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        while ((result = buffer.next()) != null) {
            result.whenComplete((r, t) -> {
                if (t != null) {
                    failedCount.incrementAndGet();
                    if (!benchmark) thrown.set(t);
                } else
                    okCount.incrementAndGet();
            });
            if (thrown.get() != null)
                sneakyThrow(thrown.get());
        }
        if (!benchmark) return Optional.empty();
        Duration duration = Duration.ofNanos(System.nanoTime() - startTime);
        double throughPut = (double)okCount.get() / duration.toMillis() * 1000D;
        return Optional.of(new BenchmarkResult(okCount.get(), failedCount.get(), duration, throughPut));
    }

    @SuppressWarnings("unchecked")
    static <T extends Throwable> void sneakyThrow(Throwable thrown) throws T { throw (T) thrown; }

    private static final JsonFactory factory = new JsonFactory();

    @Override public void close() throws IOException { client.close(); }

    private class RingBufferStream extends InputStream {

        private final byte[] b = new byte[1];
        private final InputStream in;
        private final byte[] data;
        private final int size;
        private final Object lock = new Object();
        private final JsonParser parser;
        private Throwable thrown = null;
        private long tail = 0;
        private long pos = 0;
        private long head = 0;
        private boolean done = false;

        RingBufferStream(InputStream in, int size) {
            this.in = in;
            this.data = new byte[size];
            this.size = size;

            new Thread(this::fill, "feed-reader").start();

            try { this.parser = factory.createParser(this); }
            catch (IOException e) { throw new UncheckedIOException(e); }
        }

        @Override
        public int read() throws IOException {
            return read(b, 0, 1) == -1 ? -1 : b[0];
        }

        @Override
        public int read(byte[] buffer, int off, int len) throws IOException {
            try {
                int ready;
                synchronized (lock) {
                    while ((ready = (int) (head - pos)) == 0 && ! done)
                        lock.wait();
                }
                if (thrown != null) throw new RuntimeException("Error reading input", thrown);
                if (ready == 0) return -1;

                ready = min(ready, len);
                int offset = (int) (pos % size);
                int length = min(ready, size - offset);
                System.arraycopy(data, offset, buffer, off, length);
                if (length < ready)
                    System.arraycopy(data, 0, buffer, off + length, ready - length);

                pos += ready;
                return ready;
            }
            catch (InterruptedException e) {
                throw new InterruptedIOException("Interrupted waiting for data: " + e.getMessage());
            }
        }

        void expect(JsonToken token) throws IOException {
            if (parser.nextToken() != token)
                throw new IllegalArgumentException("Expected '" + token + "' at offset " + parser.getTokenLocation().getByteOffset() +
                                                   ", but found '" + parser.currentToken() + "' (" + parser.getText() + ")");
        }

        public CompletableFuture<Result> next() throws IOException {
            long start = 0, end = -1;
            OperationType type = null;
            DocumentId id = null;
            OperationParameters parameters = protoParameters;
            switch (parser.nextToken()) {
                case END_ARRAY: return null;
                case START_OBJECT: break;
                default: throw new IllegalArgumentException("Unexpected token '" + parser.currentToken() + "' at offset " +
                                                            parser.getTokenLocation().getByteOffset());
            }

            loop: while (true) {
                switch (parser.nextToken()) {
                    case FIELD_NAME:
                        switch (parser.getText()) {
                            case "id":
                            case "put":    type = put;    id = readId(); break;
                            case "update": type = update; id = readId(); break;
                            case "remove": type = remove; id = readId(); break;
                            case "condition": parameters = parameters.testAndSetCondition(readString()); break;
                            case "create":    parameters = parameters.createIfNonExistent(readBoolean()); break;
                            case "fields": {
                                expect(START_OBJECT);
                                start = parser.getTokenLocation().getByteOffset();
                                int depth = 1;
                                while (depth > 0) switch (parser.nextToken()) {
                                    case START_OBJECT: ++depth; break;
                                    case END_OBJECT:   --depth; break;
                                }
                                end = parser.getTokenLocation().getByteOffset() + 1;
                                break;
                            }
                            default: throw new IllegalArgumentException("Unexpected field name '" + parser.getText() + "' at offset " +
                                                                        parser.getTokenLocation().getByteOffset());
                        }
                        break;

                    case END_OBJECT:
                        break loop;

                    default:
                        throw new IllegalArgumentException("Unexpected token '" + parser.currentToken() + "' at offset " +
                                                           parser.getTokenLocation().getByteOffset());
                }
            }

            if (id == null)
                throw new IllegalArgumentException("No document id for document at offset " + start);

            if (end < start)
                throw new IllegalArgumentException("No 'fields' object for document at offset " + parser.getTokenLocation().getByteOffset());

            String payload = new String(copy(start, end), UTF_8);
            synchronized (lock) {
                tail = end;
                lock.notify();
            }

            switch (type) {
                case put:    return client.put   (id, payload, parameters);
                case update: return client.update(id, payload, parameters);
                case remove: return client.remove(id,          parameters);
                default: throw new IllegalStateException("Unexpected operation type '" + type + "'");
            }
        }

        private final byte[] prefix = "{\"fields\":".getBytes(UTF_8);
        private byte[] copy(long start, long end) {
            int length = (int) (end - start);
            byte[] buffer = new byte[prefix.length + length + 1];
            System.arraycopy(prefix, 0, buffer, 0, prefix.length);

            int offset = (int) (start % size);
            int toWrite = min(length, size - offset);
            System.arraycopy(data, offset, buffer, prefix.length, toWrite);
            if (toWrite < length)
                System.arraycopy(data, 0, buffer, prefix.length + toWrite, length - toWrite);

            buffer[buffer.length - 1] = '}';
            return buffer;
        }

        private String readString() throws IOException {
            String value = parser.nextTextValue();
            if (value == null)
                throw new IllegalArgumentException("Expected '" + VALUE_STRING + "' at offset " + parser.getTokenLocation().getByteOffset() +
                                                   ", but found '" + parser.currentToken() + "' (" + parser.getText() + ")");

            return value;
        }

        private boolean readBoolean() throws IOException {
            Boolean value = parser.nextBooleanValue();
            if (value == null)
                throw new IllegalArgumentException("Expected '" + VALUE_FALSE + "' or '" + VALUE_TRUE + "' at offset " + parser.getTokenLocation().getByteOffset() +
                                                   ", but found '" + parser.currentToken() + "' (" + parser.getText() + ")");

            return value;

        }

        private DocumentId readId() throws IOException {
            return DocumentId.of(readString());
        }

        @Override
        public void close() throws IOException {
            synchronized (lock) {
                done = true;
                lock.notifyAll();
            }
            in.close();
        }

        private void fill() {
            try {
                while (true) {
                    int free;
                    synchronized (lock) {
                        while ((free = (int) (tail + size - head)) <= 0 && ! done)
                            lock.wait();
                    }
                    if (done) break;

                    int off = (int) (head % size);
                    int len = min(min(free, size - off), 1 << 13);
                    int read = in.read(data, off, len);

                    synchronized (lock) {
                        if (read < 0) done = true;
                        else head += read;
                        lock.notify();
                    }
                }
            }
            catch (Throwable t) {
                synchronized (lock) {
                    done = true;
                    thrown = t;
                }
            }
        }

    }


    public static class Builder {

        final FeedClient client;
        OperationParameters parameters = OperationParameters.empty();

        private Builder(FeedClient client) {
            this.client = requireNonNull(client);
        }

        public Builder withTimeout(Duration timeout) {
            parameters = parameters.timeout(timeout);
            return this;
        }

        public Builder withRoute(String route) {
            parameters = parameters.route(route);
            return this;
        }

        public Builder withTracelevel(int tracelevel) {
            parameters = parameters.tracelevel(tracelevel);
            return this;
        }

        public JsonStreamFeeder build() {
            return new JsonStreamFeeder(client, parameters);
        }

    }

    static class BenchmarkResult {
        final int okCount;
        final int errorCount;
        final Duration duration;
        final double throughput;

        BenchmarkResult(int okCount, int errorCount, Duration duration, double throughput) {
            this.okCount = okCount;
            this.errorCount = errorCount;
            this.duration = duration;
            this.throughput = throughput;
        }
    }

}
