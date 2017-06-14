// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor;

import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;
import com.yahoo.jrt.slobrok.api.Mirror;
import com.yahoo.jrt.slobrok.api.SlobrokList;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Thin wrapper around {@link Mirror} class, that is a bit nicer to work with.
 *
 * @author bakksjo
 */
public class JavaSlobrokMonitor {
    private final Mirror mirror;

    public JavaSlobrokMonitor(final List<String> slobroks) {
        final Supervisor supervisor = new Supervisor(new Transport());
        final SlobrokList slobrokList = new SlobrokList();
        slobrokList.setup(slobroks.toArray(new String[0]));
        mirror = new Mirror(supervisor, slobrokList);
    }

    public Map<String, String> getRegisteredServices() throws ServiceTemporarilyUnavailableException {
        if (!mirror.ready()) {
            throw new ServiceTemporarilyUnavailableException("Slobrok mirror not ready");
        }
        // TODO: Get _all_ services without resorting to a hack like this.
        return Stream.iterate("*", pattern -> pattern + "/*").limit(10)
                .flatMap(pattern -> Stream.of(mirror.lookup(pattern)))
                .collect(Collectors.toMap(Mirror.Entry::getName, Mirror.Entry::getSpec));
    }

    public void shutdown() {
        mirror.shutdown();
    }

    public static class ServiceTemporarilyUnavailableException extends Exception {
        public ServiceTemporarilyUnavailableException(final String msg) {
            super(msg);
        }
    }
}
