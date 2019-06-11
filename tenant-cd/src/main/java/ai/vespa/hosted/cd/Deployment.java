package ai.vespa.hosted.cd;

/**
 * A deployment of a Vespa application, which contains endpoints for document and metrics retrieval.
 *
 * @author jonmv
 */
public interface Deployment {

    /** Returns an Endpoint in the cluster with the "default" id. */
    Endpoint endpoint();

    /** Returns an Endpoint in the cluster with the given id. */
    Endpoint endpoint(String id);

    /** Returns a {@link TestDeployment} representation of this, or throws if this is a production deployment. */
    TestDeployment asTestDeployment();

}
