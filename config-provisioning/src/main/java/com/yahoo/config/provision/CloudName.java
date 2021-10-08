// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.Objects;

/**
 * Represents a cloud provider used in a hosted Vespa system.
 *
 * @author mpolden
 */
public class CloudName implements Comparable<CloudName> {

    private final static CloudName defaultCloud = from("default");

    private final String cloud;

    private CloudName(String cloud) {
        this.cloud = cloud;
    }

    public String value() {
        return cloud;
    }

    public boolean isDefault() {
        return defaultName().equals(this);
    }

    public static CloudName defaultName() {
        return defaultCloud;
    }

    public static CloudName from(String cloud) {
        return new CloudName(cloud);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CloudName cloudName = (CloudName) o;
        return Objects.equals(cloud, cloudName.cloud);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cloud);
    }

    @Override
    public String toString() {
        return cloud;
    }

    @Override
    public int compareTo(CloudName o) {
        return cloud.compareTo(o.cloud);
    }

}
