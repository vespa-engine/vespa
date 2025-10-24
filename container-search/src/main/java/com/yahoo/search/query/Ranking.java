// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query;

import com.yahoo.prelude.Freshness;
import com.yahoo.prelude.Location;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.QueryProfileFieldType;
import com.yahoo.search.query.profile.types.QueryProfileType;
import com.yahoo.search.query.ranking.Diversity;
import com.yahoo.search.query.ranking.GlobalPhase;
import com.yahoo.search.query.ranking.MatchPhase;
import com.yahoo.search.query.ranking.Matching;
import com.yahoo.search.query.ranking.RankFeatures;
import com.yahoo.search.query.ranking.RankProperties;
import com.yahoo.search.query.ranking.SecondPhase;
import com.yahoo.search.query.ranking.SoftTimeout;
import com.yahoo.search.query.ranking.Significance;
import com.yahoo.search.result.ErrorMessage;

import java.util.Objects;

/**
 * The ranking (hit ordering) settings of a query
 *
 * @author Arne Bergene Fossaa
 * @author bratseth
 */
public class Ranking implements Cloneable {

    /** An alias for listing features */
    public static final CompoundName RANKFEATURES = CompoundName.from("rankfeatures");

    /** The type representing the property arguments consumed by this */
    private static final QueryProfileType argumentType;
    private static final CompoundName argumentTypeName;

    public static final String RANKING = "ranking";
    public static final String LOCATION = "location";
    public static final String PROFILE = "profile";
    public static final String SORTING = "sorting";
    public static final String LIST_FEATURES = "listFeatures";
    public static final String FRESHNESS = "freshness";
    public static final String QUERYCACHE = "queryCache";
    public static final String RERANKCOUNT = "rerankCount";
    public static final String KEEPRANKCOUNT = "keepRankCount";
    public static final String RANKSCOREDROPLIMIT = "rankScoreDropLimit";
    public static final String MATCH_PHASE = "matchPhase";
    public static final String SECOND_PHASE = "secondPhase";
    public static final String GLOBAL_PHASE = "globalPhase";
    public static final String DIVERSITY = "diversity";
    public static final String SIGNIFICANCE = "significance";
    public static final String SOFTTIMEOUT = "softtimeout";
    public static final String MATCHING = "matching";
    public static final String FEATURES = "features";
    public static final String PROPERTIES = "properties";

    static {
        argumentType = new QueryProfileType(RANKING);
        argumentType.setStrict(true);
        argumentType.setBuiltin(true);
        // Note: Order here matters as fields are set in this order, and rank feature conversion depends
        //       on other fields already being set (see RankProfileInputProperties)
        argumentType.addField(new FieldDescription(PROFILE, "string", "ranking"));
        argumentType.addField(new FieldDescription(LOCATION, "string", "location"));
        argumentType.addField(new FieldDescription(SORTING, "string", "sorting sortspec"));
        argumentType.addField(new FieldDescription(LIST_FEATURES, "string", RANKFEATURES.toString()));
        argumentType.addField(new FieldDescription(FRESHNESS, "string", "datetime"));
        argumentType.addField(new FieldDescription(QUERYCACHE, "boolean"));
        argumentType.addField(new FieldDescription(RERANKCOUNT, "integer"));
        argumentType.addField(new FieldDescription(KEEPRANKCOUNT, "integer"));
        argumentType.addField(new FieldDescription(RANKSCOREDROPLIMIT, "double"));
        argumentType.addField(new FieldDescription(GLOBAL_PHASE, new QueryProfileFieldType(GlobalPhase.getArgumentType())));
        argumentType.addField(new FieldDescription(MATCH_PHASE,  new QueryProfileFieldType(MatchPhase.getArgumentType()), "matchPhase"));
        argumentType.addField(new FieldDescription(SECOND_PHASE, new QueryProfileFieldType(SecondPhase.getArgumentType())));
        argumentType.addField(new FieldDescription(DIVERSITY, new QueryProfileFieldType(Diversity.getArgumentType())));
        argumentType.addField(new FieldDescription(SOFTTIMEOUT, new QueryProfileFieldType(SoftTimeout.getArgumentType())));
        argumentType.addField(new FieldDescription(MATCHING, new QueryProfileFieldType(Matching.getArgumentType())));
        argumentType.addField(new FieldDescription(SIGNIFICANCE, new QueryProfileFieldType(Significance.getArgumentType())));
        argumentType.addField(new FieldDescription(FEATURES, "query-profile", "rankfeature input")); // Repeated at the end of RankFeatures
        argumentType.addField(new FieldDescription(PROPERTIES, "query-profile", "rankproperty"));
        argumentType.freeze();
        argumentTypeName = CompoundName.from(argumentType.getId().getName());
    }
    public static QueryProfileType getArgumentType() { return argumentType; }

    private Query parent;

    /** The location of the query is used for distance ranking */
    private Location location = null;

    /** The name of the rank profile to use */
    private String profile = null;

    /** How the query should be sorted */
    private Sorting sorting = null;

    /** Set to true to include the value of "all" rank features in the result */
    private boolean listFeatures = false;

    private Freshness freshness;

    private boolean queryCache = false;

    private Integer rerankCount = null;
    private Integer keepRankCount = null;
    private Double rankScoreDropLimit = null;

    private RankProperties rankProperties = new RankProperties();

    private RankFeatures rankFeatures;

    private MatchPhase matchPhase = new MatchPhase();

    private SecondPhase secondPhase = new SecondPhase();

    private GlobalPhase globalPhase = new GlobalPhase();

    private Matching matching = new Matching();

    private SoftTimeout softTimeout = new SoftTimeout();

    private Significance significance = new Significance();

    public Ranking(Query parent) {
        this.parent = parent;
        this.rankFeatures = new RankFeatures(this);
    }

    /**
     * Returns whether a rank profile has been explicitly set.
     *
     * This is only used in serializing the packet properly to FS4.
     */
    public boolean hasRankProfile() {
        return profile != null;
    }

    /** Get the freshness search parameters associated with this query */
    public Freshness getFreshness() {
        return freshness;
    }

    /** Set the freshness search parameters for this query */
    public void setFreshness(String dateTime) {
       try {
            Freshness freshness = new Freshness(dateTime);
            setFreshness(freshness);
        } catch (NumberFormatException e) {
           parent.errors().add(ErrorMessage.createInvalidQueryParameter("Datetime reference could not be converted from '"
                                                                        + dateTime + "' to long"));
        }
    }

    public void setFreshness(Freshness freshness) {
        this.freshness = freshness;
    }

    /**
     * Returns whether feature caching is turned on in the backed.
     * Feature caching allows us to avoid sending the query during document summary retrieval
     * and recalculate feature scores, it is typically beneficial to turn it on if
     * fan-out is low or queries are large.
     * <p>
     * Default is false (off).
     */
    public void setQueryCache(boolean queryCache) { this.queryCache = queryCache; }

    public boolean getQueryCache() { return queryCache; }

    /**
     * Sets the number of hits for which the second-phase function will be evaluated.
     * When set, this overrides the setting in the rank profile.
     */
    public void setRerankCount(int rerankCount) { this.rerankCount = rerankCount; }

    /** Returns the rerank-count that will be used, or null if not set */
    public Integer getRerankCount() { return rerankCount; }

    /** Sets the keep-rank-count that will be used, or null if not set */
    public void setKeepRankCount(int keepRankCount) { this.keepRankCount = keepRankCount; }
    /** Returns the keep-rank-count that will be used, or null if not set */
    public Integer getKeepRankCount() { return keepRankCount; }

    /** Sets the rank-score-drop-limit that will be used, or null if not set */
    public void setRankScoreDropLimit(double rankScoreDropLimit) { this.rankScoreDropLimit = rankScoreDropLimit; }
    /** Returns the rank-score-drop-limit that will be used, or null if not set */
    public Double getRankScoreDropLimit() { return rankScoreDropLimit; }

    /** Returns the location of this query, or null if none */
    public Location getLocation() { return location; }

    public void setLocation(Location location) { this.location = location; }

    /** Sets the location from a string, see {@link Location} for syntax */
    public void setLocation(String str) { this.location = new Location(str); }

    /** Returns the name of the rank profile to be used. Returns "default" if nothing is set. */
    public String getProfile() { return profile == null ? "default" : profile; }

    /** Sets the name of the rank profile to use. This cannot be set to null. */
    public void setProfile(String profile) {
        if (profile==null) throw new NullPointerException("The ranking profile cannot be set to null");
        this.profile = profile;
    }

    /**
     * Returns the rank features of this, an empty container (never null) if none are set.
     * The returned object can be modified directly to change the rank properties of this.
     */
    public RankFeatures getFeatures() {
        return rankFeatures;
    }

    /**
     * Returns the rank properties of this, an empty container (never null) if none are set.
     * The returned object can be modified directly to change the rank properties of this.
     */
    public RankProperties getProperties() {
        return rankProperties;
    }

    /** Set whether rank features should be included with the result of this query */
    public void setListFeatures(boolean listFeatures) { this.listFeatures = listFeatures; }

    /** Returns whether rank features should be dumped with the result of this query, default false */
    public boolean getListFeatures() { return listFeatures; }

    /** Returns the match phase rank settings of this. This is never null. */
    public MatchPhase getMatchPhase() { return matchPhase; }

    /** Return the second-phase rank settings of this. This is never null. */
    public SecondPhase getSecondPhase() { return secondPhase; }

    /** Returns the global-phase rank settings of this. This is never null. */
    public GlobalPhase getGlobalPhase() { return globalPhase; }

    /** Returns the matching settings of this. This is never null. */
    public Matching getMatching() { return matching; }

    /** Returns the soft timeout settings of this. This is never null. */
    public SoftTimeout getSoftTimeout() { return softTimeout; }

    /** Returns the significance settings of this. This is never null. */
    @com.yahoo.api.annotations.Beta
    public Significance getSignificance() { return significance; }

    /** Returns the sorting spec of this query, or null if none is set */
    public Sorting getSorting() { return sorting; }

    /** Sets how this query should be sorted. Set to null to turn off explicit sorting. */
    public void setSorting(Sorting sorting) {
        if (sorting == null || sorting.fieldOrders().isEmpty()) {
            this.sorting = null;
        } else {
            this.sorting = sorting;
        }
    }

    /** Sets sorting from a string. See {@link Sorting} on syntax */
    public void setSorting(String sortingString) {
        if (sortingString == null)
            setSorting((Sorting)null);
        else
            setSorting(new Sorting(sortingString, parent));
    }

    public static Ranking getFrom(Query q) {
        return (Ranking) q.properties().get(argumentTypeName);
    }

    public void prepare() {
        rankFeatures.prepare(rankProperties);
        matchPhase.prepare(rankProperties);
        secondPhase.prepare(rankProperties);
        matching.prepare(rankProperties);
        softTimeout.prepare(rankProperties);
        prepareNow(freshness);
        if (rerankCount != null)
            rankProperties.put("vespa.hitcollector.heapsize", rerankCount);
        if (keepRankCount != null)
            rankProperties.put("vespa.hitcollector.arraysize", keepRankCount);
        if (rankScoreDropLimit != null)
            rankProperties.put("vespa.hitcollector.rankscoredroplimit", rankScoreDropLimit);
    }

    private void prepareNow(Freshness freshness) {
        if (freshness == null) return;
        // TODO: See what freshness is doing with the internal props and simplify
        if (rankProperties.get("vespa.now") == null || rankProperties.get("vespa.now").isEmpty()) {
            rankProperties.put("vespa.now", "" + freshness.getRefTime());
        }
    }

    /** Assigns the query owning this */
    private void setParent(Query parent) {
        this.parent = Objects.requireNonNull(parent, "A ranking objects parent cannot be null");
    }

    /** Returns the query owning this, never null */
    public Query getParent() { return parent; }

    @Override
    public Ranking clone() {
        try {
            Ranking clone = (Ranking) super.clone();
            if (this.location != null) clone.location = this.location.clone();
            if (this.sorting != null) clone.sorting = this.sorting.clone();
            if (this.rankProperties != null) clone.rankProperties = this.rankProperties.clone();
            if (this.rankFeatures != null) clone.rankFeatures = this.rankFeatures.cloneFor(clone);
            if (this.matchPhase != null) clone.matchPhase = this.matchPhase.clone();
            if (this.secondPhase != null) clone.secondPhase = this.secondPhase.clone();
            if (this.globalPhase != null) clone.globalPhase = this.globalPhase.clone();
            if (this.matching != null) clone.matching = this.matching.clone();
            if (this.softTimeout != null) clone.softTimeout = this.softTimeout.clone();
            if (this.significance != null) clone.significance = this.significance.clone();
            return clone;
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeException("Someone inserted a noncloneable superclass",e);
        }
    }

    public Ranking cloneFor(Query parent) {
        Ranking ranking = this.clone();
        ranking.setParent(parent);
        return ranking;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if( ! (o instanceof Ranking other)) return false;

        if ( ! Objects.equals(location, other.location)) return false;
        if ( ! Objects.equals(profile, other.profile)) return false;
        if ( ! Objects.equals(sorting, other.sorting)) return false;
        if ( ! Objects.equals(freshness, other.freshness)) return false;
        if ( ! Objects.equals(queryCache, other.queryCache)) return false;
        if ( ! Objects.equals(rerankCount, other.rerankCount)) return false;
        if ( ! Objects.equals(keepRankCount, other.keepRankCount)) return false;
        if ( ! Objects.equals(rankScoreDropLimit, other.rankScoreDropLimit)) return false;
        if ( ! Objects.equals(rankProperties, other.rankProperties)) return false;
        if ( ! Objects.equals(rankFeatures, other.rankFeatures)) return false;
        if ( ! Objects.equals(matchPhase, other.matchPhase)) return false;
        if ( ! Objects.equals(secondPhase, other.secondPhase)) return false;
        if ( ! Objects.equals(globalPhase, other.globalPhase)) return false;
        if ( ! Objects.equals(matching, other.matching)) return false;
        if ( ! Objects.equals(softTimeout, other.softTimeout)) return false;
        if ( ! Objects.equals(significance, other.significance)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(location, profile, sorting, listFeatures, freshness, queryCache,
                            rerankCount, keepRankCount, rankScoreDropLimit, rankProperties,
                            rankFeatures, matchPhase, secondPhase, globalPhase, matching, softTimeout, significance);
    }

}
