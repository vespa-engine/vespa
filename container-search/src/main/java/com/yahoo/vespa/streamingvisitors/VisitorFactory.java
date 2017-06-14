// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.streamingvisitors;

import com.yahoo.messagebus.routing.Route;
import com.yahoo.search.Query;

/**
 * A factory that creates Visitors.
 *
 * @author <a href="mailto:ulf@yahoo-inc.com">Ulf Carlin</a>
 */
interface VisitorFactory {
    public Visitor createVisitor(Query query, String searchCluster, Route route);
}
