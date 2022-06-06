// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.cd;

import org.junit.jupiter.api.Tag;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Tests which run against a fully operational Vespa deployment to verify its behaviour.
 *
 * Examples of integration test types are {@link SystemTest}, {@link StagingSetup}, {@link StagingTest}, and {@link ProductionTest}.
 *
 * @author jonmv
 */
@Target({TYPE, ANNOTATION_TYPE})
@Retention(RUNTIME)
@Tag("integration")
public @interface IntegrationTest { }

