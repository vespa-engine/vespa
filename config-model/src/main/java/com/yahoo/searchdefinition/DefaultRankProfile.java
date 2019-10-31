// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.searchdefinition.document.ImmutableSDField;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The rank profile containing default settings. This is derived from the fields
 * whenever this is accessed.
 *
 * @author  bratseth
 */
public class DefaultRankProfile extends RankProfile {

    /**
     * Creates a new rank profile
     *
     * @param rankProfileRegistry The {@link com.yahoo.searchdefinition.RankProfileRegistry} to use for storing and looking up rank profiles.
     */
    public DefaultRankProfile(Search search, RankProfileRegistry rankProfileRegistry) {
        super("default", search, rankProfileRegistry);
    }

    /**
     * Does nothing, the default rank profile can not inherit anything
     */
    // TODO: Why not? If that's the case, then fail attempts at it
    public void setInherited(String inheritedName) {
    }

    /** Returns null, the default rank profile can not inherit anything */
    public String getInheritedName() {
        return null;
    }

    /** Returns the rank boost value of the given field */
    public RankSetting getRankSetting(String fieldOrIndex,RankSetting.Type type) {
        RankSetting setting = super.getRankSetting(fieldOrIndex,type);
        if (setting != null) return setting;

        ImmutableSDField field = getSearch().getConcreteField(fieldOrIndex);
        if (field != null) {
            setting = toRankSetting(field,type);
            if (setting != null)
                return setting;
        }

        Index index = getSearch().getIndex(fieldOrIndex);
        if (index != null) {
            setting = toRankSetting(index,type);
            if (setting != null)
                return setting;
        }

        return null;
    }

    private RankSetting toRankSetting(ImmutableSDField field,RankSetting.Type type) {
        if (type.equals(RankSetting.Type.WEIGHT) && field.getWeight()>0 && field.getWeight()!=100)
            return new RankSetting(field.getName(),type,field.getWeight());
        if (type.equals(RankSetting.Type.RANKTYPE))
            return new RankSetting(field.getName(),type,field.getRankType());
        if (type.equals(RankSetting.Type.LITERALBOOST) && field.getLiteralBoost()>0)
            return new RankSetting(field.getName(),type,field.getLiteralBoost());

        // Index level setting really
        if (type.equals(RankSetting.Type.PREFERBITVECTOR) && field.getRanking().isFilter()) {
            return new RankSetting(field.getName(), type, true);
        }

        return null;
    }

    private RankSetting toRankSetting(Index index, RankSetting.Type type) {
        /* TODO: Add support for indexes by adding a ranking object to the index
        if (type.equals(RankSetting.Type.PREFERBITVECTOR) && index.isPreferBitVector()) {
            return new RankSetting(index.getName(), type, new Boolean(true));
        }
        */
        return null;
    }

    /**
     * Returns the names of the fields which have a rank boost setting
     * explicitly in this profile or in fields
     */
    public Set<RankSetting> rankSettings() {
        Set<RankSetting> settings = new LinkedHashSet<>(20);
        settings.addAll(this.rankSettings);
        for (ImmutableSDField field : getSearch().allConcreteFields() ) {
            addSetting(field, RankSetting.Type.WEIGHT, settings);
            addSetting(field, RankSetting.Type.RANKTYPE, settings);
            addSetting(field, RankSetting.Type.LITERALBOOST, settings);
            addSetting(field, RankSetting.Type.PREFERBITVECTOR, settings);
        }

        // Foer settings that really pertains to indexes do the explicit indexes too
        for (Index index : getSearch().getExplicitIndices()) {
            addSetting(index, RankSetting.Type.PREFERBITVECTOR, settings);
        }
        return settings;
    }

    private void addSetting(ImmutableSDField field, RankSetting.Type type, Set<RankSetting> settings) {
        if (type.isIndexLevel()) {
            addIndexSettings(field, type, settings);
        }
        else {
            RankSetting setting = toRankSetting(field, type);
            if (setting == null) return;
            settings.add(setting);
        }
    }

    private void addIndexSettings(ImmutableSDField field, RankSetting.Type type, Set<RankSetting> settings) {
        String indexName = field.getName();

        // TODO: Make a ranking object in the index override the field level ranking object
        if (type.equals(RankSetting.Type.PREFERBITVECTOR) && field.getRanking().isFilter()) {
            settings.add(new RankSetting(indexName, type, true));
        }
    }

    private void addSetting(Index index, RankSetting.Type type, Set<RankSetting> settings) {
        RankSetting setting = toRankSetting(index, type);
        if (setting == null) return;
        settings.add(setting);
    }

}
