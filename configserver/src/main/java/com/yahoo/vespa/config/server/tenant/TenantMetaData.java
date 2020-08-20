// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.JsonDecoder;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;
import com.yahoo.text.Utf8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Metadata for a tenant. At the moment only stores last deploy time, to be used by TenantsMaintainer
 * to GC unused tenants
 *
 * @author hmusum
 */
public class TenantMetaData {

    private final Instant lastDeployTimestamp;

    public TenantMetaData(Instant instant) {
        this.lastDeployTimestamp = instant;
    }

    public Instant lastDeployTimestamp() {
        return lastDeployTimestamp;
    }

    public String asJsonString() {
        Slime slime = getSlime();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            new JsonFormat(false).encode(baos, slime);
            return baos.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Unable to encode metadata", e);
        }
    }

    public static TenantMetaData fromJsonString(String jsonString) {
        try {
            Slime data = new Slime();
            new JsonDecoder().decode(data, Utf8.toBytes(jsonString));
            Inspector root = data.get();
            Inspector lastDeployTimestamp = root.field("lastDeployTimestamp");

            return new TenantMetaData(Instant.ofEpochMilli(lastDeployTimestamp.asLong()));
        } catch (Exception e) {
            throw new IllegalArgumentException("Error parsing json metadata", e);
        }
    }

    private Slime getSlime() {
        Slime slime = new Slime();
        Cursor meta = slime.setObject();
        meta.setLong("lastDeployTimestamp", lastDeployTimestamp.toEpochMilli());
        return slime;
    }

}
