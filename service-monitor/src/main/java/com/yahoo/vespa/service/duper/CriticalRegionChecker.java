// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.duper;

import com.yahoo.vespa.service.monitor.CriticalRegion;

import java.util.ArrayList;
import java.util.List;

/**
 * To detect and throw an {@link IllegalStateException} if the execution of the current thread has
 * reached code point P, some time after the start of a critical region R and before the end of it:
 *
 * <ol>
 *     <li>Declare a static final instance of {@link CriticalRegionChecker}.</li>
 *     <li>Invoke {@link #startCriticalRegion(String)} when entering region R, and close
 *     the returned {@link CriticalRegion} when leaving it.</li>
 *     <li>Invoke {@link #assertOutsideCriticalRegions(String)} at code point P.</li>
 * </ol>
 *
 * @author hakonhall
 */
public class CriticalRegionChecker {

    private final ThreadLocalDescriptions threadLocalDescriptions = new ThreadLocalDescriptions();
    private final String name;

    public CriticalRegionChecker(String name) {
        this.name = name;
    }

    /** Start of the critical region, within which {@link #assertOutsideCriticalRegions(String)} will throw. */
    public CriticalRegion startCriticalRegion(String regionDescription) {
        List<String> regionDescriptions = threadLocalDescriptions.get();
        regionDescriptions.add(regionDescription);
        Thread threadAtStart = Thread.currentThread();

        return () -> {
            regionDescriptions.remove(regionDescription);

            Thread treadAtClose = Thread.currentThread();
            if (threadAtStart != treadAtClose) {
                throw new IllegalStateException(name + ": A critical region cannot cross threads: " +
                        regionDescription);
            }
        };
    }

    /** @throws IllegalStateException if within one or more critical regions. */
    public void assertOutsideCriticalRegions(String codePointDescription) throws IllegalStateException {
        List<String> regionDescriptions = threadLocalDescriptions.get();
        if (regionDescriptions.size() > 0) {
            throw new IllegalStateException(name + ": Code point " + codePointDescription +
                    " is within these critical regions: " + regionDescriptions);
        }
    }

    private static class ThreadLocalDescriptions extends ThreadLocal<List<String>> {
        @Override
        protected List<String> initialValue() {
            return new ArrayList<>();
        }
    }
}
