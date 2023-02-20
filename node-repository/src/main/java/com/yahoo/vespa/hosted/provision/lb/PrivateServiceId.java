package com.yahoo.vespa.hosted.provision.lb;

import ai.vespa.validation.PatternedStringWrapper;

import java.util.regex.Pattern;

/**
 * ID of a private endpoint service, such as AWS's PrivateLink, or GCP's Private Service Connect.
 *
 * @author jonmv
 */
public class PrivateServiceId extends PatternedStringWrapper<PrivateServiceId> {

    static final Pattern pattern = Pattern.compile("[a-zA-Z0-9/._-]{1,255}");

    private PrivateServiceId(String value) {
        super(value, pattern, "Private service ID");
    }

    public static PrivateServiceId of(String value) {
        return new PrivateServiceId(value);
    }

}
