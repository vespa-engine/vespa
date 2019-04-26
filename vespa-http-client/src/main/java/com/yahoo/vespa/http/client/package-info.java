// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Programmatic API for feeding to Vespa clusters independently of the
 * cluster configuration. {@link com.yahoo.vespa.http.client.Session}
 * is the central interface which is used to interact with a cluster.
 * Use {@link com.yahoo.vespa.http.client.SessionFactory} to
 * instantiate a {@link com.yahoo.vespa.http.client.Session}.
 *
 * NOTE: This is a PUBLIC API, but not annotated as such because this is not a bundle and
 *       we don't want to introduce Vespa dependencies.
 */
package com.yahoo.vespa.http.client;
