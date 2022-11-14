// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import ai.vespa.validation.PatternedStringWrapper;

import java.util.regex.Pattern;

/**
 * Identifies an account in a public cloud, such as {@link CloudName#AWS} or {@link CloudName#GCP}.
 *
 * @author mpolden
 */
public class CloudAccount extends PatternedStringWrapper<CloudAccount> {

    /** Empty value. When this is used, either implicitly or explicitly, the zone will use its default account */
    public static final CloudAccount empty = new CloudAccount("");

    private CloudAccount(String value) {
        super(value, Pattern.compile("^([0-9]{12})?$"), "cloud account");
    }

    public boolean isUnspecified() {
        return this.equals(empty);
    }

    public static CloudAccount from(String cloudAccount) {
        return switch (cloudAccount) {
            case "", "default" -> empty;
            default -> new CloudAccount(cloudAccount);
        };
    }

    @Override
    public String toString() {
        return isUnspecified() ? "unspecified account" : "account '" + value() + "'";
    }

}
