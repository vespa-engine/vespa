// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.custom;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClusterCapacityTest {

    @Test
    void serialization() throws IOException {
        ClusterCapacity clusterCapacity = new ClusterCapacity(7, 1.2, 3.4, 5.6, null, "fast", "local", "x86_64", null);
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(clusterCapacity);
        assertEquals("""
                             {"count":7,"vcpu":1.2,"memoryGb":3.4,"diskGb":5.6,"diskSpeed":"fast","storageType":"local","architecture":"x86_64"}""",
                     json);

        ClusterCapacity deserialized = mapper.readValue(json, ClusterCapacity.class);
        assertEquals(7, deserialized.count());
        assertEquals(1.2, deserialized.vcpu(), 0.0001);
        assertEquals(3.4, deserialized.memoryGb(), 0.0001);
        assertEquals(5.6, deserialized.diskGb(), 0.0001);
        assertEquals(1.0, deserialized.bandwidthGbps(), 0.0001);
        assertEquals("fast", deserialized.diskSpeed());
        assertEquals("local", deserialized.storageType());
        assertEquals("x86_64", deserialized.architecture());
    }

    @Test
    void serialization2() throws IOException {
        ClusterCapacity clusterCapacity = new ClusterCapacity(7, 1.2, 3.4, 5.6, 2.3, "any", "remote", "arm64", null);
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(clusterCapacity);
        assertEquals("""
                             {"count":7,"vcpu":1.2,"memoryGb":3.4,"diskGb":5.6,"bandwidthGbps":2.3,"diskSpeed":"any","storageType":"remote","architecture":"arm64"}""",
                     json);

        ClusterCapacity deserialized = mapper.readValue(json, ClusterCapacity.class);
        assertEquals(7, deserialized.count());
        assertEquals(1.2, deserialized.vcpu(), 0.0001);
        assertEquals(3.4, deserialized.memoryGb(), 0.0001);
        assertEquals(5.6, deserialized.diskGb(), 0.0001);
        assertEquals(2.3, deserialized.bandwidthGbps(), 0.0001);
        assertEquals("any", deserialized.diskSpeed());
        assertEquals("remote", deserialized.storageType());
        assertEquals("arm64", deserialized.architecture());
    }

    @Test
    void serialization3() throws IOException {
        ClusterCapacity clusterCapacity = new ClusterCapacity(7, 1.2, 3.4, 5.6, 2.3, "any", "remote", "arm64", "admin");
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(clusterCapacity);
        assertEquals("""
                             {"count":7,"vcpu":1.2,"memoryGb":3.4,"diskGb":5.6,"bandwidthGbps":2.3,"diskSpeed":"any","storageType":"remote","architecture":"arm64","clusterType":"admin"}""",
                     json);

        ClusterCapacity deserialized = mapper.readValue(json, ClusterCapacity.class);
        assertEquals(7, deserialized.count());
        assertEquals(1.2, deserialized.vcpu(), 0.0001);
        assertEquals(3.4, deserialized.memoryGb(), 0.0001);
        assertEquals(5.6, deserialized.diskGb(), 0.0001);
        assertEquals(2.3, deserialized.bandwidthGbps(), 0.0001);
        assertEquals("any", deserialized.diskSpeed());
        assertEquals("remote", deserialized.storageType());
        assertEquals("arm64", deserialized.architecture());
        assertEquals("admin", deserialized.clusterType());
    }

    @Test
    void serializationWithNoNodeResources() throws IOException {
        ClusterCapacity clusterCapacity = new ClusterCapacity(7, null, null, null, null, null, null, null, null);
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(clusterCapacity);
        assertEquals("{\"count\":7,\"diskSpeed\":\"fast\",\"storageType\":\"any\",\"architecture\":\"x86_64\"}", json);

        ClusterCapacity deserialized = mapper.readValue(json, ClusterCapacity.class);
        assertEquals(7, deserialized.count());
        assertEquals(0.0, deserialized.vcpu(), 0.0001);
        assertEquals(0.0, deserialized.memoryGb(), 0.0001);
        assertEquals(0.0, deserialized.diskGb(), 0.0001);
        assertEquals(1.0, deserialized.bandwidthGbps(), 0.0001);  // 1.0 is used as fallback
        assertEquals("fast", deserialized.diskSpeed());
        assertEquals("any", deserialized.storageType());
        assertEquals("x86_64", deserialized.architecture());


        // Test that using no values for diskSpeed, storageType and architecture will give expected values (the default values)
        var input = "{\"count\":7}";
        deserialized = mapper.readValue(input, ClusterCapacity.class);
        assertEquals(7, deserialized.count());
        assertEquals(0.0, deserialized.vcpu(), 0.0001);
        assertEquals(0.0, deserialized.memoryGb(), 0.0001);
        assertEquals(0.0, deserialized.diskGb(), 0.0001);
        assertEquals(1.0, deserialized.bandwidthGbps(), 0.0001);  // 1.0 is used as fallback
        assertEquals("fast", deserialized.diskSpeed());
        assertEquals("any", deserialized.storageType());
        assertEquals("x86_64", deserialized.architecture());
    }

}