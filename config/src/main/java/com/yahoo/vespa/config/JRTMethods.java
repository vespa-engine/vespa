// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.jrt.Method;
import com.yahoo.jrt.Request;

/**
 * Defines methods used for RPC config requests.
 */
public class JRTMethods {

    public static final String configV3getConfigMethodName = "config.v3.getConfig";
    private static final String configV3GetConfigRequestTypes = "s";
    private static final String configV3GetConfigResponseTypes = "sx";
    public static Method createConfigV3GetConfigMethod(Object handler, String handlerMethod) {
        return new Method(configV3getConfigMethodName, configV3GetConfigRequestTypes, configV3GetConfigResponseTypes,
                handler, handlerMethod)
                .methodDesc("get config v3")
                .paramDesc(0, "request", "config request")
                .returnDesc(0, "response", "config response")
                .returnDesc(1, "payload", "config response payload");
    }

    public static boolean checkV3ReturnTypes(Request request) {
        return request.checkReturnTypes(JRTMethods.configV3GetConfigResponseTypes);
    }
}
