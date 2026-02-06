// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import com.yahoo.api.annotations.Beta;

/**
 * Context needed to serialize a query tree.
 *
 * @param contentShare the share of the total content to be queried (across all nodes in the queried group)
 *                     which is being queried on the node we are serializing for here
 * @author bratseth
 */
@Beta
public record SerializationContext(double contentShare) {}