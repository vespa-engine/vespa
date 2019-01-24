// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordId;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;
import com.yahoo.vespa.hosted.controller.loadbalancer.LoadBalancerName;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

/**
 * @author mortent
 */
public class LoadBalancerNameSerializerTest {

    @Test
    public void test_serialization() throws IOException {
        LoadBalancerNameSerializer serializer = new LoadBalancerNameSerializer();
        ImmutableMap<ApplicationId, List<LoadBalancerName>> lbnames = ImmutableMap.of(
                ApplicationId.from("foo", "bar", "default"), Lists.newArrayList(new LoadBalancerName(new RecordId("123.4123.:124123"), RecordName.from("foo.bar"))));
        Map<ApplicationId, List<LoadBalancerName>> deserialized = serializer.fromSlime(serializer.toSlime(lbnames));

        assertThat(deserialized, equalTo(lbnames));
    }
}