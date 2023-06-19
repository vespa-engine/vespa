// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.search.logging;

import com.yahoo.search.Query;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.text.Utf8;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Base64;

public class LoggerEntry {

    private final Long timestamp;
    private final Query query;
    private final ByteBuffer blob;
    private final String track;

    private LoggerEntry(Builder builder) {
        timestamp = builder.timestamp;  // or set automatically if not set
        query = builder.query;
        blob = builder.blob;
        track = builder.track;
    }

    public Long timestamp() {
        return timestamp;
    }

    public Query query() {
        return query;
    }

    public String queryString() {
        String queryString = null;
        if (query != null) {
            URI uri = query.getUri();
            if (uri != null) {
                queryString = uri.getPath();
                if (uri.getQuery() != null) {
                    queryString += "?" + uri.getRawQuery();
                }
            }
        }
        return queryString;
    }

    public ByteBuffer blob() {
        return blob;
    }

    public String track() {
        return track;
    }

    public String toString() {
        return serialize(false);
    }

    public String serialize() { return serialize(true); }

    public String serialize(boolean encodeBlob) {
        try {
            Slime slime = new Slime();
            Cursor root = slime.setObject();

            root.setLong("timestamp", timestamp == null ? 0 : timestamp);
            root.setString("query", queryString());
            root.setString("blob", encodeBlob? Base64.getEncoder().encodeToString(blob.array()) : Utf8.toString(blob.array()));
            root.setString("track", track());

            return Utf8.toString(SlimeUtils.toJsonBytes(slime));  // TODO
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static LoggerEntry deserialize(String content) throws IOException {
        var slime = SlimeUtils.jsonToSlime(content);

        var timestamp = slime.get().field("timestamp").asLong();
        var query = new Query(slime.get().field("query").asString());
        var blob = Base64.getDecoder().decode(slime.get().field("blob").asString());
        var track = slime.get().field("track").asString();

        return new LoggerEntry(new Builder().timestamp(timestamp).query(query).blob(blob).track(track));
    }

    public static class Builder {

        private final Logger logger;

        private Long timestamp;
        private Query query;
        private ByteBuffer blob;
        private String track = "";

        // For testing
        public Builder() { this(entry -> false); }

        public Builder(Logger logger) {
            this.logger = logger;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder query(Query query) {
            this.query = query;
            return this;
        }

        public Builder blob(byte[] bytes) {
            blob = ByteBuffer.allocate(bytes.length);
            blob.put(bytes).limit(blob.position()).position(0);
            return this;
        }

        public Builder blob(String blob) {
            return this.blob(Utf8.toBytes(blob));
        }

        public Builder track(String track) {
            this.track = track;
            return this;
        }

        public boolean send() {
            return logger.send(new LoggerEntry(this));
        }

        LoggerEntry build() {
            return new LoggerEntry(this);
        }

    }

}
