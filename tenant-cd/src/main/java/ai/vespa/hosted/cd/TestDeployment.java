package ai.vespa.hosted.cd;

/**
 * A deployment of a Vespa application, which also contains endpoints for document manipulation.
 *
 * @author jonmv
 */
public interface TestDeployment extends Deployment {

    /** Returns a {@link TestEndpoint} in the cluster with the "default" id. */
    @Override
    TestEndpoint endpoint();

    /** Returns a {@link TestEndpoint} in the cluster with the given id. */
    @Override
    TestEndpoint endpoint(String id);

}
