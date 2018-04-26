// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.ranking;

import com.yahoo.search.query.Ranking;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.QueryProfileType;

import java.util.Objects;

/**
 * Holds the settings for the matching feature.
 *
 * @author baldersheim
 */
public class Matching implements Cloneable {

    /** The type representing the property arguments consumed by this */
    private static final QueryProfileType argumentType;

    public static final String TERMWISELIMIT = "termwiselimit";
    public static final String NUMTHREADSPERSEARCH = "numthreadspersearch";
    public static final String NUMSEARCHPARTITIIONS = "numsearchpartitions";
    public static final String MINHITSPERTHREAD = "minhitsperthread";


    static {
        argumentType =new QueryProfileType(Ranking.MATCHING);
        argumentType.setStrict(true);
        argumentType.setBuiltin(true);
        argumentType.addField(new FieldDescription(TERMWISELIMIT, "double"));
        argumentType.addField(new FieldDescription(NUMTHREADSPERSEARCH, "integer"));
        argumentType.addField(new FieldDescription(NUMSEARCHPARTITIIONS, "integer"));
        argumentType.addField(new FieldDescription(MINHITSPERTHREAD, "integer"));
        argumentType.freeze();
    }

    public static QueryProfileType getArgumentType() { return argumentType; }

    public Double termwiseLimit = null;
    private Integer numThreadsPerSearch = null;
    private Integer numSearchPartitions = null;
    private Integer minHitsPerThread = null;

    public Integer getNumSearchPartitions() {
        return numSearchPartitions;
    }

    public void setNumSearchPartitions(int numSearchPartitions) {
        this.numSearchPartitions = numSearchPartitions;
    }

    public Integer getMinHitsPerThread() {
        return minHitsPerThread;
    }

    public void setMinHitsPerThread(int minHitsPerThread) {
        this.minHitsPerThread = minHitsPerThread;
    }

    public void setTermwiselimit(double value) {
        if ((value < 0.0) || (value > 1.0)) {
            throw new IllegalArgumentException("termwiselimit must be in the range [0.0, 1.0]. It is " + value);
        }
        termwiseLimit = value;
    }

    public Double getTermwiseLimit() { return termwiseLimit; }

    public void setNumThreadsPerSearch(int value) {
        numThreadsPerSearch = value;
    }
    public Integer getNumThreadsPerSearch() { return numThreadsPerSearch; }


    /** Internal operation - DO NOT USE */
    public void prepare(RankProperties rankProperties) {

        if (termwiseLimit != null) {
            rankProperties.put("vespa.matching.termwise_limit", String.valueOf(termwiseLimit));
        }
        if (numThreadsPerSearch != null) {
            rankProperties.put("vespa.matching.numthreadspersearch", String.valueOf(numThreadsPerSearch));
        }
        if (numSearchPartitions != null) {
            rankProperties.put("vespa.matching.numsearchpartitions", String.valueOf(numSearchPartitions));
        }
        if (minHitsPerThread != null) {
            rankProperties.put("vespa.matching.minhitsperthread", String.valueOf(minHitsPerThread));
        }
    }

    @Override
    public Matching clone() {
        try {
            return (Matching) super.clone();
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeException("Won't happen", e);
        }
    }

    @Override
    public int hashCode() {
        int hash = 0;
        if (termwiseLimit != null) hash += 11 * termwiseLimit.hashCode();
        if (numThreadsPerSearch != null) hash += 13 * numThreadsPerSearch.hashCode();
        if (numSearchPartitions != null) hash += 17 * numSearchPartitions.hashCode();
        if (minHitsPerThread != null) hash += 19 * minHitsPerThread.hashCode();
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof Matching)) return false;

        Matching other = (Matching) o;
        if ( ! Objects.equals(this.termwiseLimit, other.termwiseLimit)) return false;
        if ( ! Objects.equals(this.numThreadsPerSearch, other.numThreadsPerSearch)) return false;
        if ( ! Objects.equals(this.numSearchPartitions, other.numSearchPartitions)) return false;
        if ( ! Objects.equals(this.minHitsPerThread, other.minHitsPerThread)) return false;
        return true;
    }

}

