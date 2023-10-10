// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Standard searchers to compose in <i>source</i> search chains (those containing searchers specific for one source and
 * which ends with a call to some provider) which calls a cluster of provider nodes. These searchers provides hashing
 * and failover of the provider nodes.
 */
@ExportPackage
@PublicApi
package com.yahoo.search.cluster;

import com.yahoo.api.annotations.PublicApi;
import com.yahoo.osgi.annotation.ExportPackage;
