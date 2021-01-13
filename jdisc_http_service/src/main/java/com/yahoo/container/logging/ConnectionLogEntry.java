// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.container.logging;

import com.fasterxml.jackson.core.JsonGenerator;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * @author mortent
 */
public class ConnectionLogEntry {

    private final UUID id;
    private final Instant timestamp;
    private final String peerAddress;
    private final Integer peerPort;

    private ConnectionLogEntry(UUID id, Instant timestamp, String peerAddress, int peerPort) {
        this.id = id;
        this.timestamp = timestamp;
        this.peerAddress = peerAddress;
        this.peerPort = peerPort;
    }

    public String toJson() throws IOException {
        Slime slime = new Slime();
        Cursor cursor = slime.setObject();
        cursor.setString("id", id.toString());
        cursor.setString("timestamp", timestamp.toString());

        setString(cursor, "peerAddress", peerAddress);
        setLong(cursor, "peerPort", peerPort);

        return new String(SlimeUtils.toJsonBytes(slime), StandardCharsets.UTF_8);
    }

    private void setString(Cursor cursor, String key, String value) {
        if(value != null) {
            cursor.setString(key, value);
        }
    }

    private void setLong(Cursor cursor, String key, Integer value) {
        if (value != null) {
            cursor.setLong(key, value);
        }
    }

    public static Builder builder(UUID id, Instant timestamp) {
        return new Builder(id, timestamp);
    }

    public String id() {
        return id.toString();
    }

    public static class Builder {
        private final UUID id;
        private final Instant timestamp;
        private String peerAddress;
        private Integer peerPort;

        Builder(UUID id, Instant timestamp) {
            this.id = id;
            this.timestamp = timestamp;
        }

        public Builder withPeerAddress(String peerAddress) {
            this.peerAddress = peerAddress;
            return this;
        }
        public Builder withPeerPort(int peerPort) {
            this.peerPort = peerPort;
            return this;
        }

        public ConnectionLogEntry build(){
            return new ConnectionLogEntry(id, timestamp, peerAddress, peerPort);
        }
    }
}
