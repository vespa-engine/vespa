// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.service.monitor;

import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;
import com.yahoo.jrt.slobrok.api.Mirror;
import com.yahoo.jrt.slobrok.api.SlobrokList;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SlobrokMonitor {
    private final Supervisor supervisor = new Supervisor(new Transport());
    private final SlobrokList slobrokList = new SlobrokList();
    private final Mirror mirror = new Mirror(supervisor, slobrokList);

    void setSlobrokConnectionSpecs(List<String> slobroks) {
        slobrokList.setup(slobroks.toArray(new String[0]));
    }

    Map<SlobrokServiceName, SlobrokServiceSpec> getRegisteredServices() {
        if (!mirror.ready()) {
            return new HashMap<>();
        }

        Mirror.Entry[] mirrorEntries = mirror.lookup("**");
        return Arrays.asList(mirrorEntries).stream().collect(Collectors.toMap(
                entry -> new SlobrokServiceName(entry.getName()),
                entry -> new SlobrokServiceSpec(entry.getSpec())));
    }

    boolean isRegistered(SlobrokServiceName serviceName) {
        return mirror.lookup(serviceName.s()).length != 0;
    }

    void shutdown() {
        mirror.shutdown();
    }

    public static class SlobrokServiceName {
        private final String name;

        SlobrokServiceName(String name) {
            this.name = name;
        }

        // TODO: Fix spec
        public String s() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            SlobrokServiceName that = (SlobrokServiceName) o;

            return name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }

    public static class SlobrokServiceSpec {
        private final String spec;

        SlobrokServiceSpec(String spec) {
            this.spec = spec;
        }

        // TODO: Fix name
        public String s() {
            return spec;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            SlobrokServiceSpec that = (SlobrokServiceSpec) o;

            return spec.equals(that.spec);
        }

        @Override
        public int hashCode() {
            return spec.hashCode();
        }
    }
}
