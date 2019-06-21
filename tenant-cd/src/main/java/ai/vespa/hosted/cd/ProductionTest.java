// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.cd;

/**
 * Tests that verify the health of production deployments of Vespa applications.
 *
 * These tests are typically run some time after deployment to a production zone, to ensure
 * the deployment is still healthy and working as expected. When these tests fail, deployment
 * of the tested change is halted until it succeeds, or is superseded by a remedying change.
 *
 * A typical production test is to verify that a set of metrics, measured by the Vespa
 * deployment itself, are within specified parameters, or that some higher-level measure
 * of quality, such as engagement among end users of the application, is as expected.
 *
 * @author jonmv
 */
public interface ProductionTest {

    /** Use with JUnit 5 @Tag to have this run in the production jobs in the pipeline. */
    String name = "ai.vespa.hosted.cd.ProductionTest";

    // Want to verify metrics (Vespa).
    // Want to verify external metrics (YAMAS, other).
    // May want to verify search gives expected results.

}
