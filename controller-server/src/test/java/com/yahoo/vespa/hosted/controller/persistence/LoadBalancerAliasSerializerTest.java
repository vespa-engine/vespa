// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.google.common.collect.ImmutableSet;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.hosted.controller.application.LoadBalancerAlias;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * @author mortent
 */
public class LoadBalancerAliasSerializerTest {

    @Test
    public void test_serialization() {
        LoadBalancerAliasSerializer serializer = new LoadBalancerAliasSerializer();
        ApplicationId owner = ApplicationId.defaultId();
        Set<LoadBalancerAlias> names = ImmutableSet.of(new LoadBalancerAlias(owner,
                                                                             "record-id-1",
                                                                             HostName.from("my-pretty-alias"),
                                                                             HostName.from("long-and-ugly-name")),
                                                       new LoadBalancerAlias(owner,
                                                                             "record-id-2",
                                                                             HostName.from("my-pretty-alias-2"),
                                                                             HostName.from("long-and-ugly-name-2")));
        Set<LoadBalancerAlias> serialized = serializer.fromSlime(owner, serializer.toSlime(names));
        assertEquals(names, serialized);
    }

}
