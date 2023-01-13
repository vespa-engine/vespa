// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import ai.vespa.validation.PatternedStringWrapper;
import ai.vespa.validation.Validation;

import java.util.regex.Pattern;

/**
 * Identifies an account in a public cloud, such as {@link CloudName#AWS} or {@link CloudName#GCP}.
 *
 * @author mpolden
 */
public class CloudAccount extends PatternedStringWrapper<CloudAccount> {

    private static final String EMPTY = "";
    private static final String AWS_ACCOUNT_ID = "[0-9]{12}";
    private static final Pattern AWS_ACCOUNT_ID_PATTERN = Pattern.compile(AWS_ACCOUNT_ID);
    private static final String GCP_PROJECT_ID = "[a-z][a-z0-9-]{4,28}[a-z0-9]";
    private static final Pattern GCP_PROJECT_ID_PATTERN = Pattern.compile(GCP_PROJECT_ID);

    /** Empty value. When this is used, either implicitly or explicitly, the zone will use its default account */
    public static final CloudAccount empty = new CloudAccount("", EMPTY, "cloud account");

    /** Verifies accountId is a valid AWS account ID, or throw an IllegalArgumentException. */
    public static void requireAwsAccountId(String accountId) {
        Validation.requireMatch(accountId, "AWS account ID", AWS_ACCOUNT_ID_PATTERN);
    }

    /** Verifies accountId is a valid GCP project ID, or throw an IllegalArgumentException. */
    public static void requireGcpProjectId(String projectId) {
        Validation.requireMatch(projectId, "GCP project ID", GCP_PROJECT_ID_PATTERN);
    }

    private CloudAccount(String value, String regex, String description) {
        super(value, Pattern.compile("^(" + regex + ")$"), description);
    }

    public boolean isUnspecified() {
        return this.equals(empty);
    }

    /** Returns true if this is an enclave account. */
    public boolean isEnclave(Zone zone) {
        return !isUnspecified() &&
               zone.system().isPublic() &&
               !equals(zone.cloud().account());
    }

    /** Verifies this account is a valid AWS account ID, or throw an IllegalArgumentException. */
    public void requireAwsAccountId() {
        requireAwsAccountId(value());
    }

    /** Verifies this account is a valid GCP project ID, or throw an IllegalArgumentException. */
    public void requireGcpProjectId() {
        requireGcpProjectId(value());
    }

    public static CloudAccount from(String cloudAccount) {
        return switch (cloudAccount) {
            // Tenants are allowed to specify "default" in services.xml.
            case "", "default" -> empty;
            default -> new CloudAccount(cloudAccount, AWS_ACCOUNT_ID + "|" + GCP_PROJECT_ID, "cloud account");
        };
    }

    @Override
    public String toString() {
        return isUnspecified() ? "unspecified account" : "account '" + value() + "'";
    }

}
