// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.protocol;

import com.yahoo.jrt.Request;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.JsonDecoder;
import com.yahoo.slime.Slime;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.config.ConfigKey;

import java.util.Optional;

/**
 * Contains slime request data objects. Provides methods for reading various fields from slime request data.
 * All data is read lazily.
 *
* @author Ulf Lilleengen
*/
class SlimeRequestData {

    private static final String REQUEST_VERSION = "version";
    private static final String REQUEST_DEF_NAME = "defName";
    private static final String REQUEST_DEF_NAMESPACE = "defNamespace";
    private static final String REQUEST_DEF_CONTENT = "defContent";
    private static final String REQUEST_CLIENT_CONFIGID = "configId";
    private static final String REQUEST_CLIENT_HOSTNAME = "clientHostname";
    private static final String REQUEST_CURRENT_GENERATION = "currentGeneration";
    private static final String REQUEST_WANTED_GENERATION = "wantedGeneration";
    private static final String REQUEST_CONFIG_MD5 = "configMD5";
    private static final String REQUEST_TRACE = "trace";
    private static final String REQUEST_TIMEOUT = "timeout";
    private static final String REQUEST_DEF_MD5 = "defMD5";
    private static final String REQUEST_COMPRESSION_TYPE = "compressionType";
    private static final String REQUEST_VESPA_VERSION = "vespaVersion";

    private final Request request;
    private Slime data = null;

    SlimeRequestData(Request request) {
        this.request = request;
    }

    SlimeRequestData(Request request, Slime data) {
        this.request = request;
        this.data = data;
    }

    private Slime getData() {
        if (data == null) {
            data = new JsonDecoder().decode(new Slime(), Utf8.toBytes(request.parameters().get(0).asString()));
        }
        return data;
    }

    private Inspector getRequestField(String requestField) {
        return getData().get().field(requestField);
    }

    ConfigKey<?> getConfigKey() {
        return ConfigKey.createFull(getRequestField(REQUEST_DEF_NAME).asString(),
                getRequestField(REQUEST_CLIENT_CONFIGID).asString(),
                getRequestField(REQUEST_DEF_NAMESPACE).asString(),
                getRequestField(REQUEST_DEF_MD5).asString());
    }

    DefContent getSchema() {
        Inspector content = getRequestField(REQUEST_DEF_CONTENT);
        return DefContent.fromSlime(content);
    }

    String getClientHostName() {
        return getRequestField(REQUEST_CLIENT_HOSTNAME).asString();
    }

    long getWantedGeneration() {
        return getRequestField(REQUEST_WANTED_GENERATION).asLong();
    }

    long getTimeout() {
        return getRequestField(REQUEST_TIMEOUT).asLong();
    }

    String getRequestConfigMd5() {
        return getRequestField(REQUEST_CONFIG_MD5).asString();
    }

    long getRequestGeneration() {
        return getRequestField(REQUEST_CURRENT_GENERATION).asLong();
    }

    static Slime encodeRequest(ConfigKey<?> key,
                               String hostname,
                               DefContent defSchema,
                               String configMd5,
                               long generation,
                               long timeout,
                               Trace trace,
                               long protocolVersion,
                               CompressionType compressionType,
                               Optional<VespaVersion> vespaVersion) {
        Slime data = new Slime();
        Cursor request = data.setObject();
        request.setLong(REQUEST_VERSION, protocolVersion);
        request.setString(REQUEST_DEF_NAME, key.getName());
        request.setString(REQUEST_DEF_NAMESPACE, key.getNamespace());
        request.setString(REQUEST_DEF_MD5, key.getMd5());
        request.setString(REQUEST_CLIENT_CONFIGID, key.getConfigId());
        request.setString(REQUEST_CLIENT_HOSTNAME, hostname);
        defSchema.serialize(request.setArray(REQUEST_DEF_CONTENT));
        request.setString(REQUEST_CONFIG_MD5, configMd5);
        request.setLong(REQUEST_CURRENT_GENERATION, generation);
        request.setLong(REQUEST_WANTED_GENERATION, 0L);
        request.setLong(REQUEST_TIMEOUT, timeout);
        request.setString(REQUEST_COMPRESSION_TYPE, compressionType.name());
        vespaVersion.ifPresent(version -> request.setString(REQUEST_VESPA_VERSION, version.toString()));
        trace.serialize(request.setObject(REQUEST_TRACE));
        return data;
    }

    Trace getRequestTrace() {
        return Trace.fromSlime(getRequestField(REQUEST_TRACE));
    }

    public CompressionType getCompressionType() {
        Inspector field = getRequestField(REQUEST_COMPRESSION_TYPE);
        return field.valid() ? CompressionType.parse(field.asString()) : CompressionType.UNCOMPRESSED;
    }

    public Optional<VespaVersion> getVespaVersion() {
        String versionString = getRequestField(REQUEST_VESPA_VERSION).asString(); // will be "" if not set, never null
        return versionString.isEmpty() ? Optional.empty() : Optional.of(VespaVersion.fromString(versionString));
    }

}
