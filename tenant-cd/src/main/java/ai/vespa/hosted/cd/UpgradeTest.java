package ai.vespa.hosted.cd;

/**
 * Tests that assert continuity of behaviour for Vespa application deployments, through upgrades.
 *
 * These tests are run whenever a change is pushed to a Vespa application, and whenever the Vespa platform
 * is upgraded, and before any deployments to production zones. When these tests fails, the tested change to
 * the Vespa application is not rolled out.
 *
 * A typical upgrade test is to do some operations against a test deployment prior to upgrade, like feed and
 * search for some documents, perhaps recording some metrics from the deployment, and then to upgrade it,
 * repeat the exercise, and compare the results from pre and post upgrade.
 *
 * TODO Split in platform upgrades and application upgrades?
 *
 * @author jonmv
 */
public interface UpgradeTest {

    // Want to verify documents are not damaged by upgrade.
    // May want to verify metrics during upgrade.

}
