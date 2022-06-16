// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.cd;

import java.time.Instant;

/**
 * A deployment of a Vespa application, which contains endpoints for document retrieval.
 *
 * @author jonmv
 */
public interface Deployment {

    /** Returns an Endpoint in the cluster with the given id. */
    Endpoint endpoint(String id);

    /** The Vespa runtime version of the deployment, e.g., 8.16.32. */
    String platform();

    /** The build number assigned to the application revision of the deployment, e.g., 496. */
    long revision();

    /** The time at which the deployment was last updated with a new platform or application version. */
    Instant deployedAt();

}
