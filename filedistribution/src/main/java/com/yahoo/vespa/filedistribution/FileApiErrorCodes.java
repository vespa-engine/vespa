// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.filedistribution;

/**
 * Error codes used in RPC responses for file distribution
 *
 * @author hmusum
 */
public enum FileApiErrorCodes {

    OK(0, "OK"),
    NOT_FOUND(1, "File reference not found"),
    TIMEOUT(2, "Timeout"),
    TRANSFER_FAILED(3, "Failed transferring file");
    private final int code;
    private final String description;

    FileApiErrorCodes(int code, String description) {
        this.code = code;
        this.description = description;
    }

    static FileApiErrorCodes get(int code) {
        for (FileApiErrorCodes error : FileApiErrorCodes.values()) {
            if (error.code() == code) {
                return error;
            }
        }
        return null;
    }

    public int code() { return code; }

    public String description() { return description; }

    @Override
    public String toString() { return code + ": " + description; }

}
