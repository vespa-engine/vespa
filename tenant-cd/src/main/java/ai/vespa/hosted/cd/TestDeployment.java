package ai.vespa.hosted.cd;

/**
 * A deployment of a Vespa application, which also contains endpoints for document manipulation.
 *
 * @author jonmv
 */
public interface TestDeployment extends Deployment {

    TestEndpoint endpoint();

    TestEndpoint endpoint(String id);

}
