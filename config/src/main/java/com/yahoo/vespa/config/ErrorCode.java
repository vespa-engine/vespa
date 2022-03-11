// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

/**
 * @author hmusum
 */
public final class ErrorCode {
    // Cannot find a config with this name, version and config md5sum
    public static final int UNKNOWN_CONFIG = 100000;
     // No config def with that name or version number
    public static final int UNKNOWN_DEFINITION = UNKNOWN_CONFIG + 1;
    public static final int UNKNOWN_DEF_MD5 = UNKNOWN_CONFIG + 4;
    public static final int UNKNOWN_VESPA_VERSION = UNKNOWN_CONFIG + 5;
    public static final int UNKNOWN_DEF_CHECKSUM = UNKNOWN_CONFIG + 6;

    public static final int ILLEGAL_NAME = UNKNOWN_CONFIG + 100;
    // Version is not a number
    public static final int ILLEGAL_VERSION = UNKNOWN_CONFIG + 101;
    public static final int ILLEGAL_CONFIGID = UNKNOWN_CONFIG + 102;
    public static final int ILLEGAL_DEF_MD5 = UNKNOWN_CONFIG + 103;
    public static final int ILLEGAL_CONFIG_MD5 = UNKNOWN_CONFIG + 104;
    // I don't think this will actually happen ...
    public static final int ILLEGAL_TIMEOUT = UNKNOWN_CONFIG + 105;
    public static final int ILLEGAL_GENERATION = UNKNOWN_CONFIG + 106;
    public static final int ILLEGAL_SUB_FLAG = UNKNOWN_CONFIG + 107;
    public static final int ILLEGAL_NAME_SPACE = UNKNOWN_CONFIG + 108;
    public static final int ILLEGAL_PROTOCOL_VERSION = UNKNOWN_CONFIG + 109;
    public static final int ILLEGAL_CLIENT_HOSTNAME = UNKNOWN_CONFIG + 110;
    public static final int ILLEGAL_DEF_CHECKSUM = UNKNOWN_CONFIG + 111;
    public static final int ILLEGAL_CONFIG_CHECKSUM = UNKNOWN_CONFIG + 112;

    // hasUpdatedConfig() is true, but generation says the config is older than previous config.
    public static final int OUTDATED_CONFIG = UNKNOWN_CONFIG + 150;

    public static final int INTERNAL_ERROR = UNKNOWN_CONFIG + 200;

    public static final int APPLICATION_NOT_LOADED = UNKNOWN_CONFIG + 300;

    public static final int INCONSISTENT_CONFIG_MD5 = UNKNOWN_CONFIG + 400;

    public static final int INCOMPATIBLE_VESPA_VERSION = UNKNOWN_CONFIG + 500;

    private ErrorCode() {
    }

    public static String getName(int error) {
        switch(error) {
            case UNKNOWN_CONFIG:             return "UNKNOWN_CONFIG";
            case UNKNOWN_DEFINITION:         return "UNKNOWN_DEFINITION";
            case UNKNOWN_DEF_MD5:            return "UNKNOWN_DEF_MD5";
            case ILLEGAL_NAME:               return "ILLEGAL_NAME";
            case ILLEGAL_VERSION:            return "ILLEGAL_VERSION";
            case ILLEGAL_CONFIGID:           return "ILLEGAL_CONFIGID";
            case ILLEGAL_DEF_MD5:            return "ILLEGAL_DEF_MD5";
            case ILLEGAL_CONFIG_MD5:         return "ILLEGAL_CONFIG_MD5";
            case ILLEGAL_TIMEOUT:            return "ILLEGAL_TIMEOUT";
            case ILLEGAL_GENERATION:         return "ILLEGAL_GENERATION";
            case ILLEGAL_SUB_FLAG:           return "ILLEGAL_SUBSCRIBE_FLAG";
            case ILLEGAL_NAME_SPACE:         return "ILLEGAL_NAME_SPACE";
            case ILLEGAL_CLIENT_HOSTNAME:    return "ILLEGAL_CLIENT_HOSTNAME";
            case OUTDATED_CONFIG:            return "OUTDATED_CONFIG";
            case INTERNAL_ERROR:             return "INTERNAL_ERROR";
            case APPLICATION_NOT_LOADED:     return "APPLICATION_NOT_LOADED";
            case ILLEGAL_PROTOCOL_VERSION:   return "ILLEGAL_PROTOCOL_VERSION";
            case INCONSISTENT_CONFIG_MD5:    return "INCONSISTENT_CONFIG_MD5";
            case UNKNOWN_VESPA_VERSION:      return "UNKNOWN_VESPA_VERSION";
            case INCOMPATIBLE_VESPA_VERSION: return "INCOMPATIBLE_VESPA_VERSION";
            default:                         return "Unknown error";
        }
    }

}
