// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.text.AbstractUtf8Array;
import com.yahoo.vespa.config.protocol.Payload;
import com.yahoo.vespa.config.util.ConfigUtils;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checksum (xxhash64) of config payload.
 *
 * @author hmusum
 */
public class PayloadChecksum {

    private static final Pattern hexChecksumPattern = Pattern.compile("[0-9a-fA-F]+");

    private final String checksum;

    public PayloadChecksum(String checksum) {
        this.checksum = checksum;
    }

    public static PayloadChecksum empty() {
        return new PayloadChecksum("");
    }

    public static PayloadChecksum fromPayload(Payload payload) {
        return fromData(payload.getData());
    }

    public static PayloadChecksum fromData(AbstractUtf8Array data) {
        return new PayloadChecksum(ConfigUtils.getXxhash64(data));
    }

    public boolean isEmpty() { return checksum.isEmpty(); }

    public String asString() { return checksum; }

    public boolean valid() {
        if (checksum.isEmpty()) return true;  // Empty checksum is ok (e.g. when running 'vespa-get-config')

        Matcher m = hexChecksumPattern.matcher(checksum);
        return m.matches();
    }

    @Override
    public int hashCode() {
        return Objects.hash(checksum);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PayloadChecksum that = (PayloadChecksum) o;
        return Objects.equals(checksum, that.checksum);
    }

    @Override
    public String toString() {
        return checksum.isEmpty() ? "" : "XXHASH64:" + checksum;
    }
}
