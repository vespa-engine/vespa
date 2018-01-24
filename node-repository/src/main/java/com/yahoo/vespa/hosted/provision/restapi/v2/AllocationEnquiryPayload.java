package com.yahoo.vespa.hosted.provision.restapi.v2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * @author smorgrav
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AllocationEnquiryPayload {

    @JsonProperty("noderepo")
    boolean initWithNodeRepo = false;

    @JsonProperty("flavors")
    List<Flavor> flavors;

    @JsonProperty("hosts")
    List<Host> hosts;

    @JsonProperty("allocations")
    List<Allocation> allocations;

    public static class Flavor {
        @JsonProperty("id")
        String id;
        @JsonProperty("mem")
        int mem;
        @JsonProperty("disk")
        int disk;
        @JsonProperty("cores")
        int cores;

        public Flavor() {}

        public Flavor(String id, int mem, int disk, int cores) {
            this.id = id;
            this.mem = mem;
            this.disk = disk;
            this.cores = cores;
        }
    }

    public static class Host {
        @JsonProperty("flavor")
        String flavor;
        @JsonProperty("count")
        int count;

        public Host() {}

        public Host(String flavor, int count) {
            this.flavor = flavor;
            this.count = count;
        }
    }

    public static class Allocation {
        @JsonProperty("id")
        String id;
        @JsonProperty("flavorid")
        String flavor;
        @JsonProperty("count")
        int count;

        public Allocation() {}

        public Allocation(String id, String flavor, int count) {
            this.id = id;
            this.flavor = flavor;
            this.count = count;
        }
    }
}
