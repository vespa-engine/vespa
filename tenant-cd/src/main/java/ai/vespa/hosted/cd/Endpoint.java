package ai.vespa.hosted.cd;

import ai.vespa.hosted.cd.metric.Metrics;

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
