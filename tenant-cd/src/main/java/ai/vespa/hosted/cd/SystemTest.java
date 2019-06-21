// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.cd;

/**
 * Tests that compare the behaviour of a Vespa application deployment against a fixed specification.
 *
 * These tests are run whenever a change is pushed to a Vespa application, and whenever the Vespa platform
 * is upgraded, and before any deployments to production zones. When these tests fails, the tested change to
 * the Vespa application is not rolled out.
 *
 * A typical system test is to feed some documents, optionally verifying that the documents have been processed
 * as expected, and then to see that queries give the expected results. Another common use is to verify integration
 * with external services.
 *
 * @author jonmv
 */
public interface SystemTest {
    // Want to feed some documents.
    // Want to verify document processing and routing is as expected.
    // Want to check recall on those documents.
    // Want to verify queries give expected documents.
    // Want to verify searchers.
    // Want to verify updates.
    // Want to verify deletion.
    // May want to verify reprocessing.
    // Must likely delete documents between tests.
    // Must be able to feed documents, setting route.
    // Must be able to search.
    // Must be able to visit.
}
