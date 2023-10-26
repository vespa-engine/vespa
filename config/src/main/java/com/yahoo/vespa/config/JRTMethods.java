// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.jrt.Method;
import com.yahoo.jrt.MethodHandler;
import com.yahoo.jrt.Request;

/**
 * Defines methods used for RPC config requests.
 */
public class JRTMethods {

    public static final String configV3getConfigMethodName = "config.v3.getConfig";
    private static final String configV3GetConfigRequestTypes = "s";
    private static final String configV3GetConfigResponseTypes = "sx";

    public static Method createConfigV3GetConfigMethod(MethodHandler methodHandler) {
        return addDescriptions(
                new Method(configV3getConfigMethodName, configV3GetConfigRequestTypes, configV3GetConfigResponseTypes, methodHandler));
    }

    private static Method addDescriptions(Method method) {
        return method.methodDesc("get config v3")
                .paramDesc(0, "request", "config request")
                .returnDesc(0, "response", "config response")
                .returnDesc(1, "payload", "config response payload");
    }

    public static boolean checkV3ReturnTypes(Request request) {
        return request.checkReturnTypes(JRTMethods.configV3GetConfigResponseTypes);
    }
}
