// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checksums of config definition payload or config payload,
 * md5 and xxhash64 are the supported types at the moment.
 *
 * @author hmusum
 */
public class PayloadChecksum {

    private static final Pattern hexChecksumPattern = Pattern.compile("[0-9a-fA-F]+");

    private final String checksum;
    private final Type type;

    public PayloadChecksum(String checksum, Type type) {
        this.checksum = checksum;
        this.type = type;
    }

    public static PayloadChecksum empty(Type type) {
        return new PayloadChecksum("", type);
    }

    public String asString() { return checksum; }

    public Type type() { return type; }

    public enum Type {MD5, XXHASH64}

    public boolean valid() {
        if (checksum.equals("")) return true;  // Empty checksum is ok (e.g. when running 'vespa-get-config')

        Matcher m = hexChecksumPattern.matcher(checksum);
        return m.matches();
    }

    @Override
    public int hashCode() {
        return Objects.hash(checksum, type);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PayloadChecksum that = (PayloadChecksum) o;
        return Objects.equals(checksum, that.checksum) && type == that.type;
    }

    @Override
    public String toString() {
        return type.name() + ":" + checksum;
    }
}
