package com.yahoo.vespa.hosted.provision.restapi.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;

/**
 * @author smorgrav
 */
public class AllocationEnquiryTest {

    @Test
    public void something() throws IOException {
        AllocationEnquiryPayload payload = new AllocationEnquiryPayload();
        payload.initWithNodeRepo = false;
        payload.hosts = new ArrayList<>();
        payload.hosts.add(new AllocationEnquiryPayload.Host("d4", 5));
        payload.hosts.add(new AllocationEnquiryPayload.Host("d8", 5));

        payload.flavors = new ArrayList<>();
        payload.flavors.add(new AllocationEnquiryPayload.Flavor("d4", 4, 4, 4));
        payload.flavors.add(new AllocationEnquiryPayload.Flavor("d8", 8, 8, 8));

        payload.allocations = new ArrayList<>();
        payload.allocations.add(new AllocationEnquiryPayload.Allocation("a", "d4", 2));
        payload.allocations.add(new AllocationEnquiryPayload.Allocation("a", "d8", 10));

        ObjectMapper mapper = new ObjectMapper();
        StringWriter writer = new StringWriter();
        mapper.writeValue(writer, payload);
        System.out.println(writer.toString());
    }
}
