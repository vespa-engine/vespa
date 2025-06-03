// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vespa.clustercontroller.core.hostinfo.ResponseStats;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A sparse mapping from distributors to error stats observed <em>by</em> that distributor
 * <em>towards</em> one particular content node (i.e. all distributor observations are for
 * the same content node). If no errors have been observed by a distributor, it will not
 * have an entry in this mapping, hence the sparseness.
 *
 * @author vekterli
 */
public class ContentNodeErrorStats {

    private final int nodeIndex;
    // TODO replace with sparse set of distributor indexes that have errors instead?
    //  This would avoid double storage of statistics.
    private final Map<Integer, DistributorErrorStats> statsFromDistributors;

    public static class DistributorErrorStats {
        private long responsesTotal;
        // This is possibly a subset of the information actually reported back from
        // distributors, but for now we only selectively care.
        private long networkErrors;

        public DistributorErrorStats(long responsesTotal, long networkErrors) {
            this.responsesTotal = responsesTotal;
            this.networkErrors = networkErrors;
        }

        public static DistributorErrorStats createEmpty() {
            return new DistributorErrorStats(0, 0);
        }

        public void add(DistributorErrorStats rhs) {
            this.networkErrors  += rhs.networkErrors;
            this.responsesTotal += rhs.responsesTotal;
        }

        public void subtract(DistributorErrorStats rhs) {
            this.networkErrors  -= rhs.networkErrors;
            this.responsesTotal -= rhs.responsesTotal;
        }

        public void merge(DistributorErrorStats rhs, int factor) {
            if (factor == 1) {
                add(rhs);
            } else {
                subtract(rhs);
            }
        }

        public long responsesTotal() { return this.responsesTotal; }
        public long networkErrors() { return this.networkErrors; }

        public double networkErrorRatio() {
            if (responsesTotal == 0) {
                return 0.0;
            }
            return networkErrors / (double)responsesTotal;
        }

        public boolean hasErrors() {
            return networkErrors > 0;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            DistributorErrorStats that = (DistributorErrorStats) o;
            return responsesTotal == that.responsesTotal &&
                    networkErrors == that.networkErrors;
        }

        @Override
        public int hashCode() {
            return Objects.hash(responsesTotal, networkErrors);
        }

        @Override
        public String toString() {
            return "DistributorErrorStats{" +
                    "responsesTotal=" + responsesTotal +
                    ", networkErrors=" + networkErrors +
                    '}';
        }

        public static DistributorErrorStats fromHostInfoStats(ResponseStats hostInfoStats) {
            return new DistributorErrorStats(hostInfoStats.totalResponseCount(), hostInfoStats.networkErrorCount());
        }
    }

    public ContentNodeErrorStats(int nodeIndex) {
        this.nodeIndex = nodeIndex;
        this.statsFromDistributors = new HashMap<>();
    }

    public ContentNodeErrorStats(int nodeIndex, Map<Integer, DistributorErrorStats> statsFromDistributors) {
        this.nodeIndex = nodeIndex;
        this.statsFromDistributors = statsFromDistributors;
    }

    public int getNodeIndex() {
        return nodeIndex;
    }

    public void addErrorStatsFrom(int distributorIndex, ContentNodeErrorStats stats) {
        mergeErrorStatsFrom(distributorIndex, stats, 1);
    }

    public void subtractErrorStatsFrom(int distributorIndex, ContentNodeErrorStats stats) {
        mergeErrorStatsFrom(distributorIndex, stats, -1);
    }

    private void mergeErrorStatsFrom(int distributorIndex, ContentNodeErrorStats stats, int factor) {
        var existing = statsFromDistributors.get(distributorIndex);
        var newStats = stats.statsFromDistributors.get(distributorIndex);
        if (existing == null && newStats != null && factor == 1) {
            existing = DistributorErrorStats.createEmpty();
            statsFromDistributors.put(distributorIndex, existing);
        }
        if (existing != null) {
            if (newStats != null) {
                existing.merge(newStats, factor);
            }
            if (factor == -1 && !existing.hasErrors()) {
                // Error stats are sparse, so if we have no more errors for the node, remove the entry.
                statsFromDistributors.remove(distributorIndex);
            }
        }
    }

    public Map<Integer, DistributorErrorStats> getStatsFromDistributors() {
        return statsFromDistributors;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ContentNodeErrorStats that = (ContentNodeErrorStats) o;
        return Objects.equals(statsFromDistributors, that.statsFromDistributors);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(statsFromDistributors);
    }

    @Override
    public String toString() {
        return String.format("{statsFromDistributors=[%s]}",
                             Arrays.toString(statsFromDistributors.entrySet().toArray()));
    }

}
