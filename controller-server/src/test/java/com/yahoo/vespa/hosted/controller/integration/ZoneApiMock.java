// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    private final CloudName cloudName;
    private final String cloudNativeRegionName;

    public static Builder newBuilder() { return new Builder(); }

    private ZoneApiMock(SystemName systemName, ZoneId id, CloudName cloudName, String cloudNativeRegionName) {
        this.systemName = systemName;
        this.id = id;
        this.cloudName = cloudName;
        this.cloudNativeRegionName = cloudNativeRegionName;
    }

    public static ZoneApiMock fromId(String id) {
        return newBuilder().withId(id).build();
    }

    public static ZoneApiMock from(Environment environment, RegionName region) {
        return newBuilder().with(ZoneId.from(environment, region)).build();
    }

    public static ZoneApiMock from(ZoneId zone) {
        return newBuilder().with(zone).build();
    }

    @Override
    public SystemName getSystemName() { return systemName; }

    @Override
    public ZoneId getId() { return id; }

    @Override
    public CloudName getCloudName() { return cloudName; }

    @Override
    public String getCloudNativeRegionName() { return cloudNativeRegionName; }

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

    public static class Builder {
        private SystemName systemName = SystemName.defaultSystem();
        private ZoneId id = ZoneId.defaultId();
        private CloudName cloudName = CloudName.defaultName();
        private String cloudNativeRegionName = id.region().value();

        public Builder with(ZoneId id) {
            this.id = id;
            return this;
        }

        public Builder withId(String id) { return with(ZoneId.from(id)); }

        public Builder with(CloudName cloudName) {
            this.cloudName = cloudName;
            return this;
        }

        public Builder withCloud(String cloud) { return with(CloudName.from(cloud)); }

        public Builder withCloudNativeRegionName(String cloudRegionName) {
            this.cloudNativeRegionName = cloudRegionName;
            return this;
        }

        public ZoneApiMock build() {
            return new ZoneApiMock(systemName, id, cloudName, cloudNativeRegionName);
        }
    }
}
