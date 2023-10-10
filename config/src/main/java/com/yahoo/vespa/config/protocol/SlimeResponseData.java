// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.protocol;

import com.yahoo.vespa.config.PayloadChecksum;
import com.yahoo.vespa.config.PayloadChecksums;
import com.yahoo.jrt.Request;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;

import static com.yahoo.vespa.config.PayloadChecksum.Type.MD5;
import static com.yahoo.vespa.config.PayloadChecksum.Type.XXHASH64;

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
    static final String RESPONSE_CONFIG_XXHASH64 = "configXxhash64";
    static final String RESPONSE_CONFIG_GENERATION = "generation";
    static final String RESPONSE_APPLY_ON_RESTART = "applyOnRestart";
    static final String RESPONSE_COMPRESSION_INFO = "compressionInfo";

    private final Request request;
    private Slime data = null;

    SlimeResponseData(Request request) {
        this.request = request;
    }

    private Slime getData() {
        if (request.returnValues().size() > 0) {
            if (data == null) {
                data = SlimeUtils.jsonToSlime(request.returnValues().get(0).asString());
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

    PayloadChecksum getResponseConfigMd5() {
        Inspector md5Field = getResponseField(RESPONSE_CONFIG_MD5);
        return md5Field.valid()
                ? new PayloadChecksum(md5Field.asString(), MD5)
                : PayloadChecksum.empty(MD5);
    }

    PayloadChecksum getResponseConfigXxhash64() {
        Inspector xxhash64Field = getResponseField(RESPONSE_CONFIG_XXHASH64);
        return xxhash64Field.valid()
                ? new PayloadChecksum(xxhash64Field.asString(), XXHASH64)
                : PayloadChecksum.empty(XXHASH64);
    }

    PayloadChecksums getResponseConfigChecksums() {
        return PayloadChecksums.from(getResponseConfigMd5(), getResponseConfigXxhash64());
    }

    CompressionInfo getCompressionInfo() {
        return CompressionInfo.fromSlime(getResponseField(RESPONSE_COMPRESSION_INFO));
    }

    boolean getResponseApplyOnRestart() {
        Inspector inspector = getResponseField(RESPONSE_APPLY_ON_RESTART);
        return inspector.valid() && inspector.asBool();
    }

}
