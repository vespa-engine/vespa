// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.vespa.config.RawConfig;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequest;

/**
 * @author hmusum
 */
interface RpcServer {

    void returnOkResponse(JRTServerConfigRequest request, RawConfig config);

    void returnErrorResponse(JRTServerConfigRequest request, int errorCode, String message);
}
