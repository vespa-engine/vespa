// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.protocol;

import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.ConfigDefinition;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.ErrorCode;

import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Static utility methods for verifying common request properties.
 *
 * @author Ulf Lilleengen
 */
public class RequestValidation {
    private static final Logger log = Logger.getLogger(RequestValidation.class.getName());

    private static final Pattern md5Pattern = Pattern.compile("[0-9a-zA-Z]+");

    public static int validateRequest(JRTConfigRequest request) {
        ConfigKey<?> key = request.getConfigKey();
        if (!RequestValidation.verifyName(key.getName())) {
            log.log(LogLevel.INFO, "Illegal name '" + key.getName() + "'");
            return ErrorCode.ILLEGAL_NAME;
        }
        if (!RequestValidation.verifyNamespace(key.getNamespace())) {
            log.log(LogLevel.INFO, "Illegal name space '" + key.getNamespace() + "'");
            return ErrorCode.ILLEGAL_NAME_SPACE;
        }
        if (!RequestValidation.verifyMd5(key.getMd5())) {
            log.log(LogLevel.INFO, "Illegal md5 sum '" + key.getNamespace() + "'");
            return ErrorCode.ILLEGAL_DEF_MD5;
        }
        if (!RequestValidation.verifyMd5(request.getRequestConfigMd5())) {
            log.log(LogLevel.INFO, "Illegal config md5 '" + request.getRequestConfigMd5() + "'");
            return ErrorCode.ILLEGAL_CONFIG_MD5;
        }
        if (!RequestValidation.verifyGeneration(request.getRequestGeneration())) {
            log.log(LogLevel.INFO, "Illegal generation '" + request.getRequestGeneration() + "'");
            return ErrorCode.ILLEGAL_GENERATION;
        }
        if (!RequestValidation.verifyTimeout(request.getTimeout())) {
            log.log(LogLevel.INFO, "Illegal timeout '" + request.getTimeout() + "'");
            return ErrorCode.ILLEGAL_TIMEOUT;
        }
        if (!RequestValidation.verifyHostname(request.getClientHostName())) {
            log.log(LogLevel.INFO, "Illegal client host name '" + request.getClientHostName() + "'");
            return ErrorCode.ILLEGAL_CLIENT_HOSTNAME;
        }
        return 0;
    }

    public static boolean verifyName(String name) {
        Matcher m = ConfigDefinition.namePattern.matcher(name);
        return m.matches();
    }

    public static boolean verifyMd5(String md5) {
        if (md5.equals("")) {
            return true;  // Empty md5 is ok (e.g. upon getconfig from command line tools)
        } else if (md5.length() != 32) {
            return false;
        }
        Matcher m = md5Pattern.matcher(md5);
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
