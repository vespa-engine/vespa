// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.cd;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Tests that assert continuity of behaviour for Vespa application deployments, through upgrades.
 *
 * Test classes annotated with this annotation are run in the second phase of automated staging tests,
 * to verify the upgraded deployment.
 * See <a href="https://cloud.vespa.ai/en/automated-deployments">Vespa cloud documentation</a>.
 *
 * @author jonmv
 */
@Target({TYPE, ANNOTATION_TYPE})
@Retention(RUNTIME)
@IntegrationTest
@Tag("staging")
public @interface StagingTest {
}
