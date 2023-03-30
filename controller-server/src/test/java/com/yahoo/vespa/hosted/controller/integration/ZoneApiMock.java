// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.integration;

import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.config.provision.zone.ZoneId;

import java.util.Objects;

/**
 * @author hakonhall
 */
public class ZoneApiMock implements ZoneApi {

    private final SystemName systemName;
    private final ZoneId id;
    private final ZoneId virtualId;
    private final CloudName cloudName;
    private final String cloudNativeRegionName;
    private final String cloudNativeAvailabilityZone;

    public static Builder newBuilder() { return new Builder(); }

    private ZoneApiMock(SystemName systemName, ZoneId id, ZoneId virtualId, CloudName cloudName, String cloudNativeRegionName, String cloudNativeAvailabilityZone) {
        this.systemName = systemName;
        this.id = id;
        this.virtualId = virtualId;
        this.cloudName = cloudName;
        this.cloudNativeRegionName = cloudNativeRegionName;
        this.cloudNativeAvailabilityZone = cloudNativeAvailabilityZone;
        if (virtualId != null && virtualId.equals(id)) {
            throw new IllegalArgumentException("Virtual ID cannot be equal to zone ID: " + id);
        }
    }

    public static ZoneApiMock fromId(String id) {
        return from(ZoneId.from(id));
    }

    public static ZoneApiMock from(Environment environment, RegionName region) {
        return from(ZoneId.from(environment, region));
    }

    public static ZoneApiMock from(ZoneId id) {
        return newBuilder().with(id).build();
    }

    @Override
    public SystemName getSystemName() { return systemName; }

    @Override
    public ZoneId getId() { return id; }

    @Override
    public ZoneId getVirtualId() {
        return virtualId == null ? getId() : virtualId;
    }

    @Override
    public CloudName getCloudName() { return cloudName; }

    @Override
    public String getCloudNativeRegionName() { return cloudNativeRegionName; }

    @Override
    public String getCloudNativeAvailabilityZone() { return cloudNativeAvailabilityZone; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ZoneApiMock that = (ZoneApiMock) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return id.toString();
    }

    public static class Builder {

        private SystemName systemName = SystemName.defaultSystem();
        private ZoneId id = ZoneId.defaultId();
        private ZoneId virtualId = null;
        private CloudName cloudName = CloudName.DEFAULT;
        private String cloudNativeRegionName = id.region().value();
        private String cloudNativeAvailabilityZone = "az1";

        public Builder with(ZoneId id) {
            this.id = id;
            return this;
        }

        public Builder withSystem(SystemName systemName) {
            this.systemName = systemName;
            return this;
        }

        public Builder withId(String id) {
            return with(ZoneId.from(id));
        }

        public Builder withVirtualId(ZoneId virtualId) {
            this.virtualId = virtualId;
            return this;
        }

        public Builder withVirtualId(String virtualId) {
            return withVirtualId(ZoneId.from(virtualId));
        }

        public Builder with(CloudName cloudName) {
            this.cloudName = cloudName;
            return this;
        }

        public Builder withCloud(String cloud) { return with(CloudName.from(cloud)); }

        public Builder withCloudNativeRegionName(String cloudRegionName) {
            this.cloudNativeRegionName = cloudRegionName;
            return this;
        }

        public Builder withCloudNativeAvailabilityZone(String cloudAvailabilityZone) {
            this.cloudNativeAvailabilityZone = cloudAvailabilityZone;
            return this;
        }

        public ZoneApiMock build() {
            return new ZoneApiMock(systemName, id, virtualId, cloudName, cloudNativeRegionName, cloudNativeAvailabilityZone);
        }
    }

}
