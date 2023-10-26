// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.tenant;

import com.yahoo.config.provision.TenantName;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.text.Utf8;

import java.io.IOException;
import java.time.Instant;

/**
 * Meta data for a tenant like tenant name, tenant creation time and last deployment time, stored in ZooKeeper
 *
 * @author hmusum
 */
public class TenantMetaData {

    private static final String createTimestampKey = "createTimestampKey";
    private static final String lastDeployTimestampKey = "lastDeployTimestamp";

    private final TenantName tenantName;
    private final Instant created;
    private final Instant lastDeployed;

    public TenantMetaData(TenantName tenantName, Instant lastDeployed, Instant created) {
        this.tenantName = tenantName;
        this.created = created;
        this.lastDeployed = lastDeployed;
    }

    public TenantMetaData withLastDeployTimestamp(Instant deployTimestamp) {
        return new TenantMetaData(tenantName, deployTimestamp, created);
    }

    public TenantName tenantName() {
        return tenantName;
    }

    public Instant lastDeployTimestamp() {
        return lastDeployed;
    }

    public Instant createdTimestamp() {
        return created;
    }

    public byte[] asJsonBytes() {
        try {
            return SlimeUtils.toJsonBytes(getSlime());
        } catch (IOException e) {
            throw new RuntimeException("Unable to encode metadata", e);
        }
    }

    public static TenantMetaData fromJsonString(TenantName tenantName, String jsonString) {
        try {
            Slime data = SlimeUtils.jsonToSlime(jsonString);
            Inspector root = data.get();
            Inspector created = root.field(createTimestampKey);
            Inspector lastDeployTimestamp = root.field(lastDeployTimestampKey);

            return new TenantMetaData(tenantName,
                                      Instant.ofEpochMilli(lastDeployTimestamp.asLong()),
                                      Instant.ofEpochMilli(created.asLong()));
        } catch (Exception e) {
            throw new IllegalArgumentException("Error parsing json metadata", e);
        }
    }

    private Slime getSlime() {
        Slime slime = new Slime();
        Cursor meta = slime.setObject();
        meta.setLong(createTimestampKey, created.toEpochMilli());
        meta.setLong(lastDeployTimestampKey, lastDeployed.toEpochMilli());
        return slime;
    }

    @Override
    public String toString() {
        return tenantName + ": " + Utf8.toString(asJsonBytes());
    }

}
