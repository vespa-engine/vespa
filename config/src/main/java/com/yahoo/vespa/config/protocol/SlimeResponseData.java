// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.protocol;

import com.yahoo.jrt.Request;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.JsonDecoder;
import com.yahoo.slime.Slime;
import com.yahoo.text.Utf8;

/**
 * Contains response data for a slime response and methods for decoding the response data that
 * are common to all {@link Slime} based config requests.
 *
 * @author Ulf Lilleengen
 */
class SlimeResponseData {

    static final String RESPONSE_VERSION = "version";
    static final String RESPONSE_DEF_NAME = "defName";
    static final String RESPONSE_DEF_NAMESPACE = "defNamespace";
    static final String RESPONSE_DEF_MD5 = "defMD5";
    static final String RESPONSE_CONFIGID = "configId";
    static final String RESPONSE_CLIENT_HOSTNAME = "clientHostname";
    static final String RESPONSE_TRACE = "trace";
    static final String RESPONSE_CONFIG_MD5 = "configMD5";
    static final String RESPONSE_CONFIG_GENERATION = "generation";
    static final String RESPONSE_INTERNAL_REDEPLOY = "internalRedeploy";
    static final String RESPONSE_COMPRESSION_INFO = "compressionInfo";

    private final Request request;
    private Slime data = null;

    SlimeResponseData(Request request) {
        this.request = request;
    }

    private Slime getData() {
        if (request.returnValues().size() > 0) {
            if (data == null) {
                data = new JsonDecoder().decode(new Slime(), Utf8.toBytes(request.returnValues().get(0).asString()));
            }
            return data;
        } else {
            return new Slime();
        }
    }

    Inspector getResponseField(String responseTrace) {
        return getData().get().field(responseTrace);
    }

    long getResponseConfigGeneration() {
        Inspector inspector = getResponseField(RESPONSE_CONFIG_GENERATION);
        return inspector.valid() ? inspector.asLong() : -1;
    }

    Trace getResponseTrace() {
        Inspector trace = getResponseField(RESPONSE_TRACE);
        return trace.valid() ? Trace.fromSlime(trace) : Trace.createDummy();
    }

    String getResponseConfigMd5() {
        Inspector inspector = getResponseField(RESPONSE_CONFIG_MD5);
        return inspector.valid() ? inspector.asString() : "";
    }

    CompressionInfo getCompressionInfo() {
        return CompressionInfo.fromSlime(getResponseField(RESPONSE_COMPRESSION_INFO));
    }

    boolean getResponseInternalRedeployment() {
        Inspector inspector = getResponseField(RESPONSE_INTERNAL_REDEPLOY);
        return inspector.valid() ? inspector.asBool() : false;
    }

}
