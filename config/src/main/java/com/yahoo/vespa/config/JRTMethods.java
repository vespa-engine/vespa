// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.jrt.Method;
import com.yahoo.jrt.Request;

/**
 * Defines methods used for RPC config requests.
 */
public class JRTMethods {

    static final String getConfigMethodName = "getConfig";
    private static final String getConfigRequestTypes = "sssssll";
    static final String getConfigResponseTypes = "sssssilS";

    static final String configV1getConfigMethodName = "config.v1.getConfig";
    private static final String configV1GetConfigRequestTypes = "sssssllsSi";
    static final String configV1GetConfigResponseTypes = "sssssilSs";

    /**
     * Creates a Method object for the RPC method getConfig.
     *
     * @param handler       the object that will handle the method call
     * @param handlerMethod the method belonging to the handler that will handle the method call
     * @return a Method
     */
    public static Method createGetConfigMethod(Object handler, String handlerMethod) {
        return new Method(getConfigMethodName, getConfigRequestTypes, getConfigResponseTypes,
                handler, handlerMethod)
                .methodDesc("get config")
                .paramDesc(0, "defName", "config class definition name")
                .paramDesc(1, "defVersion", "config class definition version")
                .paramDesc(2, "defMD5", "md5sum for config class definition")
                .paramDesc(3, "configid", "config id")
                .paramDesc(4, "configMD5", "md5sum for last got config, empty string if unknown")
                .paramDesc(5, "timestamp",
                        "timestamp for last got config, only relevant to the server if useTimestamp != 0")
                .paramDesc(6, "timeout", "timeout (milliseconds) before answering request if config is unchanged")
                .returnDesc(0, "defName", "config name")
                .returnDesc(1, "defVersion", "config version")
                .returnDesc(2, "defMD5", "md5sum for config class definition")
                .returnDesc(3, "configid", "requested config id")
                .returnDesc(4, "configMD5", "md5sum for this config")
                .returnDesc(5, "changed", "changed flag (1 if config changed, 0 otherwise")
                .returnDesc(6, "timestamp", "timestamp when config was last changed")
                .returnDesc(7, "payload", "config payload for the requested config");
    }

    /**
     * Creates a Method object for the RPC method config.v1.getConfig. Use both for
     * getting config and subscribing to config
     *
     * @param handler       the object that will handle the method call
     * @param handlerMethod the method belonging to the handler that will handle the method call
     * @return a Method
     */
    public static Method createConfigV1GetConfigMethod(Object handler, String handlerMethod) {
        return new Method(configV1getConfigMethodName, configV1GetConfigRequestTypes, configV1GetConfigResponseTypes,
                handler, handlerMethod)
                .methodDesc("get config v1")
                .paramDesc(0, "defName", "config class definition name")
                .paramDesc(1, "defVersion", "config class definition version")
                .paramDesc(2, "defMD5", "md5sum for config class definition")
                .paramDesc(3, "configid", "config id")
                .paramDesc(4, "configMD5", "md5sum for last got config, empty string if unknown")
                .paramDesc(5, "generation",
                        "generation for last got config, only relevant to the server if generation != 0")
                .paramDesc(6, "timeout", "timeout (milliseconds) before answering request if config is unchanged")
                .paramDesc(7, "namespace", "namespace for defName")
                .paramDesc(8, "defContent", "config definition content")
                .paramDesc(9, "subscribe", "subscribe to config (1) or not (0)")
                .returnDesc(0, "defName", "config name")
                .returnDesc(1, "defVersion", "config version")
                .returnDesc(2, "defMD5", "md5sum for config class definition")
                .returnDesc(3, "configid", "requested config id")
                .returnDesc(4, "configMD5", "md5sum for this config")
                .returnDesc(5, "changed", "changed flag (1 if config changed, 0 otherwise") // TODO Maybe remove?
                .returnDesc(6, "generation", "generation of config")
                .returnDesc(7, "payload", "config payload for the requested config")
                .returnDesc(8, "namespace", "namespace for defName");
    }

    public static final String configV2getConfigMethodName = "config.v2.getConfig";
    private static final String configV2GetConfigRequestTypes = "s";
    private static final String configV2GetConfigResponseTypes = "s";
    public static Method createConfigV2GetConfigMethod(Object handler, String handlerMethod) {
        return new Method(configV2getConfigMethodName, configV2GetConfigRequestTypes, configV2GetConfigResponseTypes,
                handler, handlerMethod)
                .methodDesc("get config v2")
                .paramDesc(0, "request", "config request")
                .returnDesc(0, "response", "config response");
    }

    public static boolean checkV2ReturnTypes(Request request) {
        return request.checkReturnTypes(JRTMethods.configV2GetConfigResponseTypes);
    }

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
