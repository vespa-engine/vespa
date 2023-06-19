// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Identifies an account in a public cloud, such as {@link CloudName#AWS} or {@link CloudName#GCP}.
 *
 * @author mpolden
 */
public class CloudAccount implements Comparable<CloudAccount> {

    private record CloudMeta(String accountType, Pattern pattern) {
        private boolean matches(String account) { return pattern.matcher(account).matches(); }
    }
    private static final Map<String, CloudMeta> META_BY_CLOUD = Map.of(
            "aws", new CloudMeta("Account ID", Pattern.compile("[0-9]{12}")),
            "gcp", new CloudMeta("Project ID", Pattern.compile("[a-z][a-z0-9-]{4,28}[a-z0-9]")));

    /** Empty value. When this is used, either implicitly or explicitly, the zone will use its default account */
    public static final CloudAccount empty = new CloudAccount("", CloudName.DEFAULT);

    private final String account;
    private final CloudName cloudName;

    private CloudAccount(String account, CloudName cloudName) {
        this.account = account;
        this.cloudName = cloudName;
    }

    public String account() { return account; }
    public CloudName cloudName() { return cloudName; }

    /** Returns the serialized value of this account that can be deserialized with {@link CloudAccount#from} */
    public final String value() {
        if (isUnspecified()) return account;
        return cloudName.value() + ':' + account;
    }

    public boolean isUnspecified() {
        return this.equals(empty);
    }

    /** Returns true if this is an exclave account. */
    public boolean isExclave(Zone zone) {
        return !isUnspecified() &&
               zone.system().isPublic() &&
               !equals(zone.cloud().account());
    }

    @Override
    public String toString() {
        return isUnspecified() ? "unspecified account" : "account '" + account + "' in " + cloudName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CloudAccount that = (CloudAccount) o;
        return account.equals(that.account) && cloudName.equals(that.cloudName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(account, cloudName);
    }

    @Override
    public int compareTo(CloudAccount o) {
        return this.value().compareTo(o.value());
    }


    public static CloudAccount from(String cloudAccount) {
        int index = cloudAccount.indexOf(':');
        if (index < 0) {
            // Tenants are allowed to specify "default" in services.xml.
            if (cloudAccount.isEmpty() || cloudAccount.equals("default"))
                return empty;
            if (META_BY_CLOUD.get("aws").matches(cloudAccount))
                return new CloudAccount(cloudAccount, CloudName.AWS);
            if (META_BY_CLOUD.get("gcp").matches(cloudAccount)) // TODO (freva): Remove July 2023
                return new CloudAccount(cloudAccount, CloudName.GCP);
            throw illegal(cloudAccount, "Must be on format '<cloud-name>:<account>' or 'default'");
        }

        String cloud = cloudAccount.substring(0, index);
        String account = cloudAccount.substring(index + 1);
        CloudMeta cloudMeta = META_BY_CLOUD.get(cloud);
        if (cloudMeta == null)
            throw illegal(cloudAccount, "Cloud name must be one of: " + META_BY_CLOUD.keySet().stream().sorted().collect(Collectors.joining(", ")));

        if (!cloudMeta.matches(account))
            throw illegal(cloudAccount, cloudMeta.accountType + " must match '" + cloudMeta.pattern.pattern() + "'");
        return new CloudAccount(account, CloudName.from(cloud));
    }

    private static IllegalArgumentException illegal(String cloudAccount, String details) {
        return new IllegalArgumentException("Invalid cloud account '" + cloudAccount + "': " + details);
    }

}
