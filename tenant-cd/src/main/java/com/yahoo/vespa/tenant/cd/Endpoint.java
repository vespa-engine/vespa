package com.yahoo.vespa.tenant.cd;

import com.yahoo.vespa.tenant.cd.metrics.Metrics;

/**
 * An endpoint in a Vespa application {@link Deployment}, which allows document and metrics retrieval.
 *
 * The endpoint translates {@link Query}s to {@link Search}s, and {@link Selection}s to {@link Visit}s.
 * It also supplies {@link Metrics}.
 *
 * @author jonmv
 */
public interface Endpoint {

    Search search(Query query);

    Visit visit(Selection selection);

    Metrics metrics();

}
