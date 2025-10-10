// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.vespasignificance.merge;

/**
 * Allows at max half of the maximum allowed number of open file descriptors by the operating system.
 *
 * @author johsol
 */
public class HalfSystemLimitBudget implements FileHandleBudget {

    private final long maxOsFileHandles;

    public HalfSystemLimitBudget() {
        this.maxOsFileHandles = UlimitProbe.softLimit();
    }

    @Override
    public long maxReadersAllowed() {
        return maxOsFileHandles / 2;
    }

}
