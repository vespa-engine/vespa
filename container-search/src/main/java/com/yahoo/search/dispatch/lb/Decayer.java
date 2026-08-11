// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.lb;

import com.yahoo.search.dispatch.RequestDuration;

/**
 * Implementations of this provide a specific definition of "recent average cost"
 * of a query in a group.
 *
 * @author Olli Virtanen
 */
interface Decayer {

    void decay(RequestDuration duration);

    double averageCost();

}
