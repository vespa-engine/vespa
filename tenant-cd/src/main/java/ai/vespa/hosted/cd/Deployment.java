// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.cd;

/**
 * A deployment of a Vespa application, which contains endpoints for document and metrics retrieval.
 *
 * @author jonmv
 */
public interface Deployment {

    /** Returns an Endpoint in the cluster with the given id. */
    Endpoint endpoint(String id);

}
