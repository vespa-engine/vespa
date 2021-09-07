// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription.impl;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checksums of config definition payload or config payload,
 * md5 and xxhash64 are the supported types at the moment.
 *
 * @author hmusum
 */
public class PayloadChecksum {

    private static final Pattern hexChecksumPattern = Pattern.compile("[0-9a-zA-Z]+");

    private final String checksum;
    private final Type type;

    public PayloadChecksum(String checksum) {
        this.checksum = checksum;
        this.type = Type.MD5;
    }

    public static PayloadChecksum empty() {
        return new PayloadChecksum("");
    }

    public String asString() { return checksum; }

    public Type type() { return type; }

    public enum Type {MD5, XXHASH64}

    public boolean valid() {
        if (checksum.equals("")) return true;  // Empty checksum is ok (e.g. when running 'vespa-get-config')

        if (type == Type.MD5 && checksum.length() != 32) {
            return false;
        } else if (type == Type.XXHASH64 && checksum.length() != 16) {
            return false;
        }

        Matcher m = hexChecksumPattern.matcher(checksum);
        return m.matches();
    }

}
