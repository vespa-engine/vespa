// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.flags.custom;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.yahoo.test.json.Jackson;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CustomerRpmServiceTest {

    @Test
    void customer_rpm_services_deserialize() throws JsonProcessingException {
        String json = """
                {
                    "services": [
                        {
                            "url": "https://some.website.com/rpm1",
                            "memoryLimitMb": 200.0
                        },
                        {
                            "url": "https://some.website.com/rpm2",
                            "memoryLimitMb": 300.0,
                            "cpuLimitNanoSeconds": 100.0
                        }
                   ]
                }
                """;

        CustomerRpmServiceList serviceList = Jackson.mapper().readValue(json, CustomerRpmServiceList.class);
        assertEquals(2, serviceList.services().size());

        Optional<CustomerRpmService> service1 = serviceList.services().stream()
                .filter(r -> r.url().equals("https://some.website.com/rpm1"))
                .findFirst();
        assertEquals(200.0, service1.get().memoryLimitMb());

        Optional<CustomerRpmService> service2 = serviceList.services().stream()
                .filter(r -> r.url().equals("https://some.website.com/rpm2"))
                .findFirst();
        assertEquals(300.0, service2.get().memoryLimitMb());
        assertEquals(Optional.of(100.0), service2.get().cpuLimitNanoSeconds());

        // Empty variant
        CustomerRpmServiceList empty = Jackson.mapper().readValue("{\"services\": []}", CustomerRpmServiceList.class);
        assertEquals(true, empty.services().isEmpty());

        // Ignore other fields
        CustomerRpmServiceList ignoredFields = Jackson.mapper().readValue("{\"services\": [], \"someOtherField\": 123 }", CustomerRpmServiceList.class);
        assertEquals(true, empty.services().isEmpty());

        // Invalid service configuration
        var invalidJson = "{\"services\": [ { \"badUrlField\": \"no_thanks\" }]}";
        assertThrows(JsonProcessingException.class, () -> Jackson.mapper().readValue(invalidJson, CustomerRpmServiceList.class));
    }

    @Test
    void customer_rpm_services_serialize() throws JsonProcessingException {
        CustomerRpmService service1 = new CustomerRpmService("https://some.website.com/rpm1", 123.4, null);
        CustomerRpmService service2 = new CustomerRpmService("https://some.website.com/rpm2", 567.8, 100.0);
        CustomerRpmServiceList serviceList = new CustomerRpmServiceList(List.of(service1, service2));
        var mapper = Jackson.mapper();
        String serialized = mapper.writeValueAsString(serviceList);
        CustomerRpmServiceList deserialized = mapper.readValue(serialized, CustomerRpmServiceList.class);
        assertEquals(serviceList, deserialized);
    }
}