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

    public static final CloudAccount empty = new CloudAccount("");
    public static final CloudAccount default_ = new CloudAccount("default");

    public CloudAccount(String value) {
        super(value, Pattern.compile("^([0-9]{12}|default)?$"), "cloud account");
    }

    public boolean isEmpty() {
        return this.equals(empty);
    }

    public boolean isDefault() { return this.equals(default_); }

    @Override
    public String toString() {
        return isEmpty() ? "unspecified account" : "account '" + value() + "'";
    }

}
