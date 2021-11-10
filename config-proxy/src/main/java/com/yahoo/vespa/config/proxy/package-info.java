// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
    Provides the classes for the Vespa config proxy.

    <p>The {@link com.yahoo.vespa.config.proxy.ProxyServer ProxyServer}
    contains the main method that instantiates a new ProxyServer that
    listens for incoming config requests from clients at a given
    port. The ProxyServer handles communication with the remote server,
    and keeps a cache of {@link com.yahoo.vespa.config.RawConfig RawConfig}
    objects.
    </p>

    <p>This package is very slim, due to extensive reuse of low-level code from
    the config client library.
    </p>

    <h2>Why Vespa needs a config proxy</h2>

    <p>It is possible for a client to subscribe
    to config from the config server directly. However, if all Vespa
    services applied this philosophy, it would cause a tremendous load
    on the server that would need to handle a very large number of
    requests from individual clients. Even more importantly, it would
    inflict a huge load on the network if all config requests were
    sent to the remote server.
    </p>

    <p>
    Typically, one config proxy is running on each host in a Vespa
    installation, so each subscriber on that host sends requests to
    the proxy and never to the remote server. The proxy is responsible
    for keeping that config up-to-date on behalf of all clients that
    subscribe to it. This means that multiple subscribers to the same
    config (and using the same config id) maps to only one request
    from the proxy to the external server.
    </p>

    <p>Another advantage with a local config proxy on each node is
    that subscribers become less vulnerable to network
    failures. Should the network or the remote server go down for a
    short period, only the proxy will notice, hence removing overhead from
    the subscribers.
    </p>
*/

@com.yahoo.api.annotations.PackageMarker
package com.yahoo.vespa.config.proxy;
