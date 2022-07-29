// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Simon Thoresen Hult
 */
public class SchemaCoverageTest {

    @Test
    void requireThatAccessorWork() {
        SearchCoverage coverage = new SearchCoverage.Builder()
                .setMinimum(0.1)
                .setMinWaitAfterCoverageFactor(0.2)
                .setMaxWaitAfterCoverageFactor(0.3)
                .build();
        assertEquals(0.1, coverage.getMinimum(), 1E-6);
        assertEquals(0.2, coverage.getMinWaitAfterCoverageFactor(), 1E-6);
        assertEquals(0.3, coverage.getMaxWaitAfterCoverageFactor(), 1E-6);
    }

    @Test
    void requireThatDefaultsAreNull() {
        SearchCoverage search = new SearchCoverage.Builder().build();
        assertNull(search.getMinimum());
        assertNull(search.getMinWaitAfterCoverageFactor());
        assertNull(search.getMaxWaitAfterCoverageFactor());
    }

    @Test
    void requireThatInvalidMinimumCanNotBeSet() {
        SearchCoverage.Builder coverage = new SearchCoverage.Builder();
        coverage.setMinimum(0.5);
        assertEquals(0.5, coverage.build().getMinimum(), 1E-6);
        try {
            coverage.setMinimum(-0.5);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Expected value in range [0, 1], got -0.5.", e.getMessage());
        }
        assertEquals(0.5, coverage.build().getMinimum(), 1E-6);
        try {
            coverage.setMinimum(1.5);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Expected value in range [0, 1], got 1.5.", e.getMessage());
        }
        assertEquals(0.5, coverage.build().getMinimum(), 1E-6);
    }

    @Test
    void requireThatInvalidMinWaitAfterCoverageFactorCanNotBeSet() {
        SearchCoverage.Builder coverage = new SearchCoverage.Builder();
        coverage.setMinWaitAfterCoverageFactor(0.5);
        assertEquals(0.5, coverage.build().getMinWaitAfterCoverageFactor(), 1E-6);
        try {
            coverage.setMinWaitAfterCoverageFactor(-0.5);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Expected value in range [0, 1], got -0.5.", e.getMessage());
        }
        assertEquals(0.5, coverage.build().getMinWaitAfterCoverageFactor(), 1E-6);
        try {
            coverage.setMinWaitAfterCoverageFactor(1.5);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Expected value in range [0, 1], got 1.5.", e.getMessage());
        }
        assertEquals(0.5, coverage.build().getMinWaitAfterCoverageFactor(), 1E-6);
    }

    @Test
    void requireThatInvalidMaxWaitAfterCoverageFactorCanNotBeSet() {
        SearchCoverage.Builder coverage = new SearchCoverage.Builder();
        coverage.setMaxWaitAfterCoverageFactor(0.5);
        assertEquals(0.5, coverage.build().getMaxWaitAfterCoverageFactor(), 1E-6);
        try {
            coverage.setMaxWaitAfterCoverageFactor(-0.5);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Expected value in range [0, 1], got -0.5.", e.getMessage());
        }
        assertEquals(0.5, coverage.build().getMaxWaitAfterCoverageFactor(), 1E-6);
        try {
            coverage.setMaxWaitAfterCoverageFactor(1.5);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Expected value in range [0, 1], got 1.5.", e.getMessage());
        }
        assertEquals(0.5, coverage.build().getMaxWaitAfterCoverageFactor(), 1E-6);
    }

    @Test
    void requireThatMinWaitCanNotBeSetLargerThanMaxWait() {
        SearchCoverage.Builder coverage = new SearchCoverage.Builder();
        coverage.setMaxWaitAfterCoverageFactor(0.5);
        coverage.setMinWaitAfterCoverageFactor(0.4);
        assertEquals(0.4, coverage.build().getMinWaitAfterCoverageFactor(), 1E-6);
        coverage.setMinWaitAfterCoverageFactor(0.5);
        assertEquals(0.5, coverage.build().getMinWaitAfterCoverageFactor(), 1E-6);
        try {
            coverage.setMinWaitAfterCoverageFactor(0.6);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Minimum wait (got 0.6) must be no larger than maximum wait (was 0.5).", e.getMessage());
        }
        assertEquals(0.5, coverage.build().getMinWaitAfterCoverageFactor(), 1E-6);
    }

    @Test
    void requireThatMaxWaitCanNotBeSetSmallerThanMaxWait() {
        SearchCoverage.Builder coverage = new SearchCoverage.Builder();
        coverage.setMinWaitAfterCoverageFactor(0.5);
        coverage.setMaxWaitAfterCoverageFactor(0.6);
        assertEquals(0.6, coverage.build().getMaxWaitAfterCoverageFactor(), 1E-6);
        coverage.setMaxWaitAfterCoverageFactor(0.5);
        assertEquals(0.5, coverage.build().getMaxWaitAfterCoverageFactor(), 1E-6);
        try {
            coverage.setMaxWaitAfterCoverageFactor(0.4);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Maximum wait (got 0.4) must be no smaller than minimum wait (was 0.5).", e.getMessage());
        }
        assertEquals(0.5, coverage.build().getMaxWaitAfterCoverageFactor(), 1E-6);
    }
}
