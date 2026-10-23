// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author hakonhall
 */
public class SystemNameTest {
    @Test
    void test() {
        for (SystemName name : SystemName.values()) {
            assertEquals(name, SystemName.from(name.value()));
        }
    }

    @Test
    void allOf() {
        assertEquals(Set.of(SystemName.cd, SystemName.PublicCd, SystemName.kubernetesCd, SystemName.DevCloud), SystemName.allOf(SystemName::isCdLike));
        assertEquals(Set.of(SystemName.PublicCd, SystemName.Public, SystemName.DevCloud), SystemName.allOf(SystemName::isPublicCloudLike));
        assertEquals(Set.of(SystemName.DevCloud), SystemName.allOf(SystemName::isDevCloud));
    }

    @Test
    void devCloud() {
        assertEquals(SystemName.DevCloud, SystemName.from("devcloud"));
        assertEquals(false, SystemName.DevCloud.isProductionLike());
        assertEquals(true, SystemName.DevCloud.isHostedLike());
        assertEquals(true, SystemName.hostedVespa().contains(SystemName.DevCloud));
    }
}
