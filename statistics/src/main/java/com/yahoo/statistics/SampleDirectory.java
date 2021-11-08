// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.statistics;

import java.util.ArrayList;
import java.util.List;

/**
 * Book-keeping class to know which SampleSet instances have unlogged data.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
@Deprecated
final class SampleDirectory {
    private final Object directoryLock = new Object();
    private List<SampleSet> directory = new ArrayList<>(200);

    void put(SampleSet s) {
        synchronized (directoryLock) {
            directory.add(s);
            s.setRegisteredForLogging(true);
        }
    }

    /**
     * Get a view of the current generation of data and instantiate a new
     * generation. This does the memory barrier two-step for the
     * client.
     */
    SampleSet.Sampling[] fetchValues() {
        SampleSet.Sampling[] copyToReturn;
        synchronized (directoryLock) {
            List<SampleSet> tmpDir = directory;
            copyToReturn = new SampleSet.Sampling[tmpDir.size()];
            List<SampleSet> newDir = new ArrayList<>(200);
            for (int i = 0; i < copyToReturn.length; ++i) {
                copyToReturn[i] = tmpDir.get(i).getAndReset();
            }
            directory = newDir;
        }
        return copyToReturn;
    }

    /**
     * Return a view of the current generation of data. This does the memory
     * barrier two-step for the client.
     */
    SampleSet.Sampling[] viewValues() {
        SampleSet.Sampling[] copy;
        synchronized (directoryLock) {
            copy = new SampleSet.Sampling[directory.size()];
            for (int i = 0; i < copy.length; ++i) {
                copy[i] = directory.get(i).values;
            }
        }
        return copy;
    }

}
