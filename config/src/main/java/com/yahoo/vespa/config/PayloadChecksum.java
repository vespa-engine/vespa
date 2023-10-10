// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.text.AbstractUtf8Array;
import com.yahoo.vespa.config.protocol.Payload;
import com.yahoo.vespa.config.util.ConfigUtils;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.yahoo.vespa.config.PayloadChecksum.Type.MD5;
import static com.yahoo.vespa.config.PayloadChecksum.Type.XXHASH64;

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

    public static PayloadChecksum fromPayload(Payload payload, Type type) {
        switch (type) {
            case MD5: return fromMd5Data(payload.getData());
            case XXHASH64: return fromXxhash64Data(payload.getData());
            default: throw new IllegalArgumentException("Unknown type " + type);
        }
    }

    private static PayloadChecksum fromMd5Data(AbstractUtf8Array data) {
        return new PayloadChecksum(ConfigUtils.getMd5(data), MD5);
    }

    private static PayloadChecksum fromXxhash64Data(AbstractUtf8Array data) {
        return new PayloadChecksum(ConfigUtils.getXxhash64(data), XXHASH64);
    }

    public boolean isEmpty() {
        switch (type) {
            case MD5: return this.equals(empty(MD5));
            case XXHASH64: return this.equals(empty(XXHASH64));
            default: throw new IllegalArgumentException("Unknown type " + type);
        }
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
