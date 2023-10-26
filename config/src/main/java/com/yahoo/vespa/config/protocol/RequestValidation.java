// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.protocol;

import com.yahoo.vespa.config.ConfigDefinition;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.ErrorCode;
import com.yahoo.vespa.config.PayloadChecksum;

import java.util.logging.Logger;
import java.util.regex.Matcher;

import static com.yahoo.vespa.config.PayloadChecksum.Type.MD5;
import static java.util.logging.Level.WARNING;

/**
 * Static utility methods for verifying common request properties.
 *
 * @author Ulf Lilleengen
 */
public class RequestValidation {
    private static final Logger log = Logger.getLogger(RequestValidation.class.getName());

    public static int validateRequest(JRTConfigRequest request) {
        ConfigKey<?> key = request.getConfigKey();
        if (!RequestValidation.verifyName(key.getName())) {
            log.log(WARNING, "Illegal name '" + key.getName() + "'");
            return ErrorCode.ILLEGAL_NAME;
        }
        if (!RequestValidation.verifyNamespace(key.getNamespace())) {
            log.log(WARNING, "Illegal name space '" + key.getNamespace() + "'");
            return ErrorCode.ILLEGAL_NAME_SPACE;
        }
        if (!(new PayloadChecksum(request.getRequestDefMd5(), MD5).valid())) {
            log.log(WARNING, "Illegal checksum '" + key.getNamespace() + "'");
            return ErrorCode.ILLEGAL_DEF_MD5;  // TODO: Use ILLEGAL_DEF_CHECKSUM
        }
        if (! request.getRequestConfigChecksums().valid()) {
            log.log(WARNING, "Illegal config checksum '" + request.getRequestConfigChecksums() + "'");
            return ErrorCode.ILLEGAL_CONFIG_MD5; // TODO: Use ILLEGAL_CONFIG_CHECKSUM
        }
        if (!RequestValidation.verifyGeneration(request.getRequestGeneration())) {
            log.log(WARNING, "Illegal generation '" + request.getRequestGeneration() + "'");
            return ErrorCode.ILLEGAL_GENERATION;
        }
        if (!RequestValidation.verifyTimeout(request.getTimeout())) {
            log.log(WARNING, "Illegal timeout '" + request.getTimeout() + "'");
            return ErrorCode.ILLEGAL_TIMEOUT;
        }
        if (!RequestValidation.verifyHostname(request.getClientHostName())) {
            log.log(WARNING, "Illegal client host name '" + request.getClientHostName() + "'");
            return ErrorCode.ILLEGAL_CLIENT_HOSTNAME;
        }
        return 0;
    }

    public static boolean verifyName(String name) {
        Matcher m = ConfigDefinition.namePattern.matcher(name);
        return m.matches();
    }

    public static boolean verifyTimeout(Long timeout) {
        return (timeout > 0);
    }

    private static boolean verifyGeneration(Long generation) {
        return (generation >= 0);
    }

    private static boolean verifyNamespace(String namespace) {
        Matcher m = ConfigDefinition.namespacePattern.matcher(namespace);
        return m.matches();
    }

    private static boolean verifyHostname(String clientHostName) {
        return !("".equals(clientHostName));
    }

}
