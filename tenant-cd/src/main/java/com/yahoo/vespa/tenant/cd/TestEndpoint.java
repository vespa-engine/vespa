package com.yahoo.vespa.tenant.cd;

import java.util.concurrent.Future;

/**
 * An endpoint in a Vespa application {@link TestDeployment}, which also translates {@link Feed}s to {@link Digest}s.
 *
 * @author jonmv
 */
public interface TestEndpoint extends Endpoint {

    /** Sends the given Feed to this TestEndpoint, blocking until it is digested, and returns a feed report. */
    Digest digest(Feed feed);

}
