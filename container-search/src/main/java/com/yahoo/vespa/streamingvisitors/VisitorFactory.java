// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.streamingvisitors;

import com.yahoo.messagebus.routing.Route;
import com.yahoo.search.Query;

/**
 * A factory that creates Visitors.
 *
 * @author Ulf Carlin
 */
interface VisitorFactory {

    Visitor createVisitor(Query query, String searchCluster, Route route, String documentType, int traceLevelOverride);

}
