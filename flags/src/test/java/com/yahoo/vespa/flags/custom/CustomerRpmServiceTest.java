// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.flags.custom;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.yahoo.test.json.Jackson;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CustomerRpmServiceTest {

    @Test
    void customer_rpm_services_deserialize() throws JsonProcessingException {
        String json = """
                {
                    "services": [
                        {
                            "unit": "example1",
                            "memory": 200.0
                        },
                        {
                            "unit": "example2",
                            "memory": 300.0,
                            "cpu": 1.0
                        },
                        {
                            "unit": "example3",
                            "package": "package3",
                            "memory": 400.0
                        }
                   ]
                }
                """;

        CustomerRpmServiceList serviceList = Jackson.mapper().readValue(json, CustomerRpmServiceList.class);
        assertEquals(3, serviceList.services().size());

        Optional<CustomerRpmService> service1 = serviceList.services().stream()
                .filter(r -> r.unitName().equals("example1"))
                .findFirst();
        assertEquals("example1", service1.get().packageName());
        assertEquals(200.0, service1.get().memoryLimitMib());

        Optional<CustomerRpmService> service2 = serviceList.services().stream()
                .filter(r -> r.unitName().equals("example2"))
                .findFirst();
        assertEquals("example2", service2.get().packageName());
        assertEquals(300.0, service2.get().memoryLimitMib());
        assertEquals(Optional.of(1.0), service2.get().cpuLimitCores());

        Optional<CustomerRpmService> service3 = serviceList.services().stream()
                .filter(r -> r.unitName().equals("example3"))
                .findFirst();
        assertEquals("package3", service3.get().packageName());
        assertEquals(300.0, service2.get().memoryLimitMib());

        // Empty variant
        CustomerRpmServiceList empty = Jackson.mapper().readValue("{\"services\": []}", CustomerRpmServiceList.class);
        assertTrue(empty.services().isEmpty());

        // Ignore other fields
        CustomerRpmServiceList ignoredFields = Jackson.mapper().readValue("{\"services\": [], \"someOtherField\": 123 }", CustomerRpmServiceList.class);
        assertTrue(empty.services().isEmpty());

        // Invalid service configuration
        var invalidJson = "{\"services\": [{ \"missingUnitName\": \"no_thanks\" }]}";
        assertThrows(JsonProcessingException.class, () -> Jackson.mapper().readValue(invalidJson, CustomerRpmServiceList.class));

        // Negative CPU treated as no limit
        var negCpuJson = "{\"services\": [{ \"unit\": \"test\", \"memory\": 100.0, \"cpu\": -1.0 }]}";
        CustomerRpmService negCpu = Jackson.mapper().readValue(negCpuJson, CustomerRpmServiceList.class).services().get(0);
        assertEquals(Optional.empty(), negCpu.cpuLimitCores());
        assertThrows(JsonProcessingException.class, () -> Jackson.mapper().readValue(invalidJson, CustomerRpmServiceList.class));
    }

    @Test
    void customer_rpm_services_serialize() throws JsonProcessingException {
        CustomerRpmService service1 = new CustomerRpmService("foo", null, 123.4, null);
        CustomerRpmService service2 = new CustomerRpmService("bar",  null, 567.8, 1.0);
        CustomerRpmService service3 = new CustomerRpmService("dog",  "pack", 500.0, 0.3);
        CustomerRpmServiceList serviceList = new CustomerRpmServiceList(List.of(service1, service2, service3));
        var mapper = Jackson.mapper();
        String serialized = mapper.writeValueAsString(serviceList);
        CustomerRpmServiceList deserialized = mapper.readValue(serialized, CustomerRpmServiceList.class);
        assertEquals(serviceList, deserialized);
    }
}