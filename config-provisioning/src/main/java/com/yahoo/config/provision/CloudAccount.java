// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import ai.vespa.validation.PatternedStringWrapper;

import java.util.regex.Pattern;

/**
 * Identifies an account in a public cloud, such as AWS or GCP.
 *
 * @author mpolden
 */
public class CloudAccount extends PatternedStringWrapper<CloudAccount> {

    public CloudAccount(String value) {
        super(value, Pattern.compile("^[0-9]{12}$"), "cloud account");
    }

}
