// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.custom;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClusterCapacityTest {
    @Test
    void serialization() throws IOException {
        ClusterCapacity clusterCapacity = new ClusterCapacity(7, 1.2, 3.4, 5.6, null);
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(clusterCapacity);
        assertEquals("{\"count\":7,\"vcpu\":1.2,\"memoryGb\":3.4,\"diskGb\":5.6}", json);

        ClusterCapacity deserialized = mapper.readValue(json, ClusterCapacity.class);
        assertEquals(1.2, deserialized.vcpu(), 0.0001);
        assertEquals(3.4, deserialized.memoryGb(), 0.0001);
        assertEquals(5.6, deserialized.diskGb(), 0.0001);
        assertEquals(1.0, deserialized.bandwidthGbps(), 0.0001);
        assertEquals(7, deserialized.count());
    }

    @Test
    void serialization2() throws IOException {
        ClusterCapacity clusterCapacity = new ClusterCapacity(7, 1.2, 3.4, 5.6, 2.3);
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(clusterCapacity);
        assertEquals("{\"count\":7,\"vcpu\":1.2,\"memoryGb\":3.4,\"diskGb\":5.6,\"bandwidthGbps\":2.3}", json);

        ClusterCapacity deserialized = mapper.readValue(json, ClusterCapacity.class);
        assertEquals(1.2, deserialized.vcpu(), 0.0001);
        assertEquals(3.4, deserialized.memoryGb(), 0.0001);
        assertEquals(5.6, deserialized.diskGb(), 0.0001);
        assertEquals(2.3, deserialized.bandwidthGbps(), 0.0001);
        assertEquals(7, deserialized.count());
    }
}