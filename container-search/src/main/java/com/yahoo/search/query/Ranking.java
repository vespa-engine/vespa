// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query;

import com.yahoo.processing.request.CompoundName;
import com.yahoo.prelude.Freshness;
import com.yahoo.prelude.Location;
import com.yahoo.search.Query;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.QueryProfileFieldType;
import com.yahoo.search.query.profile.types.QueryProfileType;
import com.yahoo.search.query.ranking.Diversity;
import com.yahoo.search.query.ranking.MatchPhase;
import com.yahoo.search.query.ranking.Matching;
import com.yahoo.search.query.ranking.RankFeatures;
import com.yahoo.search.query.ranking.RankProperties;
import com.yahoo.search.query.ranking.SoftTimeout;
import com.yahoo.search.result.ErrorMessage;

/**
 * The ranking (hit ordering) settings of a query
 *
 * @author Arne Bergene Fossaa
 * @author bratseth
 */
public class Ranking implements Cloneable {

    /** An alias for listing features */
    public static final com.yahoo.processing.request.CompoundName RANKFEATURES =
            new com.yahoo.processing.request.CompoundName("rankfeatures");

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
    public static final String MATCH_PHASE = "matchPhase";
    public static final String DIVERSITY = "diversity";
    public static final String SOFTTIMEOUT = "softtimeout";
    public static final String MATCHING = "matching";
    public static final String FEATURES = "features";
    public static final String PROPERTIES = "properties";

    static {
        argumentType =new QueryProfileType(RANKING);
        argumentType.setStrict(true);
        argumentType.setBuiltin(true);
        argumentType.addField(new FieldDescription(LOCATION, "string", "location"));
        argumentType.addField(new FieldDescription(PROFILE, "string", "ranking"));
        argumentType.addField(new FieldDescription(SORTING, "string", "sorting sortspec"));
        argumentType.addField(new FieldDescription(LIST_FEATURES, "string", RANKFEATURES.toString()));
        argumentType.addField(new FieldDescription(FRESHNESS, "string", "datetime"));
        argumentType.addField(new FieldDescription(QUERYCACHE, "string"));
        argumentType.addField(new FieldDescription(MATCH_PHASE,  new QueryProfileFieldType(MatchPhase.getArgumentType()), "matchPhase"));
        argumentType.addField(new FieldDescription(DIVERSITY, new QueryProfileFieldType(Diversity.getArgumentType())));
        argumentType.addField(new FieldDescription(SOFTTIMEOUT, new QueryProfileFieldType(SoftTimeout.getArgumentType())));
        argumentType.addField(new FieldDescription(MATCHING, new QueryProfileFieldType(Matching.getArgumentType())));
        argumentType.addField(new FieldDescription(FEATURES, "query-profile", "rankfeature"));
        argumentType.addField(new FieldDescription(PROPERTIES, "query-profile", "rankproperty"));
        argumentType.freeze();
        argumentTypeName=new CompoundName(argumentType.getId().getName());
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

    private RankProperties rankProperties = new RankProperties();

    private RankFeatures rankFeatures = new RankFeatures();

    private MatchPhase matchPhase = new MatchPhase();

    private Matching matching = new Matching();

    private SoftTimeout softTimeout = new SoftTimeout();

    public Ranking(Query parent) {
        this.parent = parent;
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

    /** Returns the matching settings of this. This is never null. */
    public Matching getMatching() { return matching; }

    /** Returns the soft timeout settings of this. This is never null. */
    public SoftTimeout getSoftTimeout() { return softTimeout; }

    @Override
    public Object clone() {
        try {
            Ranking clone = (Ranking) super.clone();

            if (sorting != null) clone.sorting = this.sorting.clone();

            clone.rankProperties = this.rankProperties.clone();
            clone.rankFeatures = this.rankFeatures.clone();
            clone.matchPhase = this.matchPhase.clone();
            clone.matching = this.matching.clone();
            clone.softTimeout = this.softTimeout.clone();
            return clone;
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeException("Someone inserted a noncloneable superclass",e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if( ! (o instanceof Ranking)) return false;

        Ranking other = (Ranking) o;

        if ( ! QueryHelper.equals(rankProperties, other.rankProperties)) return false;
        if ( ! QueryHelper.equals(rankFeatures, other.rankFeatures)) return false;
        if ( ! QueryHelper.equals(freshness, other.freshness)) return false;
        if ( ! QueryHelper.equals(this.sorting, other.sorting)) return false;
        if ( ! QueryHelper.equals(this.location, other.location)) return false;
        if ( ! QueryHelper.equals(this.profile, other.profile)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash += 11 * rankFeatures.hashCode();
        hash += 13 * rankProperties.hashCode();
        hash += 17 * matchPhase.hashCode();
        hash += 19 * softTimeout.hashCode();
        hash += 23 * matching.hashCode();
        return Ranking.class.hashCode() + QueryHelper.combineHash(sorting,location,profile,hash);
    }

    /** Returns the sorting spec of this query, or null if none is set */
    public Sorting getSorting() { return sorting; }

    /** Sets how this query should be sorted. Set to null to turn off explicit sorting. */
    public void setSorting(Sorting sorting) { this.sorting = sorting; }

    /** Sets sorting from a string. See {@link Sorting} on syntax */
    public void setSorting(String sortingString) {
        if (sortingString==null)
            setSorting((Sorting)null);
        else
            setSorting(new Sorting(sortingString));
    }

    public static Ranking getFrom(Query q) {
        return (Ranking) q.properties().get(argumentTypeName);
    }

    public void prepare() {
        rankFeatures.prepare(rankProperties);
        matchPhase.prepare(rankProperties);
        matching.prepare(rankProperties);
        softTimeout.prepare(rankProperties);
        prepareNow(freshness);
    }

    private void prepareNow(Freshness freshness) {
        if (freshness == null) return;
        // TODO: See what freshness is doing with the internal props and simplify
        if (rankProperties.get("vespa.now") == null || rankProperties.get("vespa.now").isEmpty()) {
            rankProperties.put("vespa.now", "" + freshness.getRefTime());
        }
    }

}
