// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

/**
 * @author hmusum
 */
public enum ErrorType {
    TRANSIENT, FATAL;

    public static ErrorType getErrorType(int errorCode) {
        switch (errorCode) {
            case com.yahoo.jrt.ErrorCode.CONNECTION:
            case com.yahoo.jrt.ErrorCode.TIMEOUT:
                return ErrorType.TRANSIENT;
            case ErrorCode.UNKNOWN_CONFIG:
            case ErrorCode.UNKNOWN_DEFINITION:
            case ErrorCode.UNKNOWN_DEF_MD5:
            case ErrorCode.ILLEGAL_NAME:
            case ErrorCode.ILLEGAL_VERSION:
            case ErrorCode.ILLEGAL_CONFIGID:
            case ErrorCode.ILLEGAL_DEF_MD5:
            case ErrorCode.ILLEGAL_CONFIG_MD5:
            case ErrorCode.ILLEGAL_TIMEOUT:
            case ErrorCode.OUTDATED_CONFIG:
            case ErrorCode.INTERNAL_ERROR:
            case ErrorCode.APPLICATION_NOT_LOADED:
            case ErrorCode.UNKNOWN_VESPA_VERSION:
            case ErrorCode.ILLEGAL_PROTOCOL_VERSION:
            case ErrorCode.INCONSISTENT_CONFIG_MD5:
                return ErrorType.FATAL;
            default:
                return ErrorType.FATAL;
        }
    }
}
