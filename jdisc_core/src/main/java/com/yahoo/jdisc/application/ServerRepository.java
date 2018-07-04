// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import com.google.common.collect.ImmutableList;
import com.yahoo.jdisc.Container;
import com.yahoo.jdisc.service.ServerProvider;
import org.osgi.framework.Bundle;

import java.util.*;
import java.util.logging.Logger;

/**
 * This is a repository of {@link ServerProvider}s. An instance of this class is owned by the {@link ContainerBuilder},
 * and is used to configure the set of ServerProviders that eventually become part of the active {@link Container}.
 *
 * @author Simon Thoresen Hult
 */
public class ServerRepository implements Iterable<ServerProvider> {

    private static final Logger log = Logger.getLogger(ServerRepository.class.getName());
    private final List<ServerProvider> servers = new LinkedList<>();
    private final GuiceRepository guice;

    public ServerRepository(GuiceRepository guice) {
        this.guice = guice;
    }

    public Iterable<ServerProvider> activate() { return ImmutableList.copyOf(servers); }

    public List<ServerProvider> installAll(Bundle bundle, Iterable<String> serverNames) throws ClassNotFoundException {
        List<ServerProvider> lst = new LinkedList<>();
        for (String serverName : serverNames) {
            lst.add(install(bundle, serverName));
        }
        return lst;
    }

    public ServerProvider install(Bundle bundle, String serverName) throws ClassNotFoundException {
        log.finer("Installing server provider '" + serverName + "'.");
        Class<?> namedClass = bundle.loadClass(serverName);
        Class<ServerProvider> serverClass = ContainerBuilder.safeClassCast(ServerProvider.class, namedClass);
        ServerProvider server = guice.getInstance(serverClass);
        install(server);
        return server;
    }

    public void installAll(Iterable<? extends ServerProvider> servers) {
        for (ServerProvider server : servers) {
            install(server);
        }
    }

    public void install(ServerProvider server) {
        servers.add(server);
    }

    public void uninstallAll(Iterable<? extends ServerProvider> handlers) {
        for (ServerProvider handler : handlers) {
            uninstall(handler);
        }
    }

    public void uninstall(ServerProvider handler) {
        servers.remove(handler);
    }

    public Collection<ServerProvider> collection() {
        return Collections.unmodifiableCollection(servers);
    }

    @Override
    public Iterator<ServerProvider> iterator() {
        return collection().iterator();
    }
}
