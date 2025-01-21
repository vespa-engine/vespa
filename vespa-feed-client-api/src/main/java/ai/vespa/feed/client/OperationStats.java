// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Statistics for feed operations over HTTP against a Vespa cluster.
 *
 * @author jonmv
 * @author bjorncs
 */
public class OperationStats {

    private final double duration;
    private final long requests;
    private final long inflight;
    private final long targetInflight;
    private final long exceptions;
    private final long bytesSent;
    private final Map<Integer, Response> statsByCode;

    public OperationStats(double duration, long requests, long exceptions, long inflight, long targetInFlight, long bytesSent,
                          Map<Integer, Response> statsByCode) {
        this.duration = duration;
        this.requests = requests;
        this.exceptions = exceptions;
        this.inflight = inflight;
        this.targetInflight = targetInFlight;
        this.bytesSent = bytesSent;
        this.statsByCode = statsByCode;
    }

    /** Number of HTTP requests attempted. */
    public long requests() {
        return requests;
    }

    /** Number of HTTP responses received. */
    public long responses() {
        return statsByCode.values().stream().mapToLong(r -> r.count).sum();
    }

    /** Number of 200 OK HTTP responses received. */
    public long successes() {
        var okStats = statsByCode.get(200);
        if (okStats == null) return 0;
        return okStats.count;
    }

    /** Statistics per response code. */
    public Map<Integer, Response> statsByCode() { return statsByCode; }

    /** Number of exceptions (instead of responses). */
    public long exceptions() {
        return exceptions;
    }

    /** Number of attempted requests which haven't yielded a response or exception yet. */
    public long inflight() {
        return inflight;
    }

    /** Average request-response latency, or -1.  */
    public long averageLatencyMillis() {
        var requests = requests();
        if (requests == 0) return -1;
        var totalLatencyMillis = statsByCode.values().stream().mapToLong(r -> r.totalLatencyMillis).sum();
        return totalLatencyMillis / requests;
    }

    /** Minimum request-response latency, or -1.  */
    public long minLatencyMillis() {
        return statsByCode.values().stream().mapToLong(r -> r.minLatencyMillis).min().orElse(-1L);
    }

    /** Maximum request-response latency, or -1.  */
    public long maxLatencyMillis() {
        return statsByCode.values().stream().mapToLong(r -> r.maxLatencyMillis).max().orElse(-1L);
    }

    /** Number of bytes sent, for HTTP requests with a response. */
    public long bytesSent() {
        return bytesSent;
    }

    /** Number of bytes received in HTTP responses. */
    public long bytesReceived() {
        return statsByCode.values().stream().mapToLong(r -> r.bytesReceived).sum();
    }

    @Override
    public String toString() {
        return "OperationStats{" +
                "duration=" + duration +
                ", inflight=" + inflight +
                ", targetInflight=" + targetInflight +
                ", exceptions=" + exceptions +
                ", bytesSent=" + bytesSent +
                ", statsByCode=" + statsByCode +
                '}';
    }

    public static class Response {
        private final long count;
        private final long totalLatencyMillis;
        private final long averageLatencyMillis;
        private final long minLatencyMillis;
        private final long maxLatencyMillis;
        private final long bytesReceived;
        private final double rate;

        public Response(
                long count, long totalLatencyMillis, long averageLatencyMillis, long minLatencyMillis,
                long maxLatencyMillis, long bytesReceived, double rate) {
            this.count = count;
            this.totalLatencyMillis = totalLatencyMillis;
            this.averageLatencyMillis = averageLatencyMillis;
            this.minLatencyMillis = minLatencyMillis;
            this.maxLatencyMillis = maxLatencyMillis;
            this.bytesReceived = bytesReceived;
            this.rate = rate;
        }

        // Generate getters for all fields. Should have the same name as the field, and written as a single line
        public long count() { return count; }
        public long averageLatencyMillis() { return averageLatencyMillis; }
        public long minLatencyMillis() { return minLatencyMillis; }
        public long maxLatencyMillis() { return maxLatencyMillis; }
        public long bytesReceived() { return bytesReceived; }
        public double rate() { return rate; }

        @Override
        public String toString() {
            return "Response{" +
                    "count=" + count +
                    ", totalLatencyMillis=" + totalLatencyMillis +
                    ", averageLatencyMillis=" + averageLatencyMillis +
                    ", minLatencyMillis=" + minLatencyMillis +
                    ", maxLatencyMillis=" + maxLatencyMillis +
                    ", bytesReceived=" + bytesReceived +
                    ", rate=" + rate +
                    '}';
        }
    }

}
