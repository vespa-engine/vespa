// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.document.RankType;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.RankProfile;
import com.yahoo.searchdefinition.Search;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Level;

/**
 * Takes the fields and indexes that are of type rank filter, and stores those names on all rank profiles
 *
 * @author Vegard Havdal
 */
public class FilterFieldNames extends Processor {

    public FilterFieldNames(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate) {
        for (SDField f : search.allConcreteFields()) {
            if (f.getRanking().isFilter()) {
                filterField(f.getName());
            }
        }

        for (RankProfile profile : rankProfileRegistry.localRankProfiles(search)) {
            Set<String> filterFields = new LinkedHashSet<>();
            findFilterFields(search, profile, filterFields);
            for (Iterator<String> itr = filterFields.iterator(); itr.hasNext(); ) {
                String fieldName = itr.next();
                profile.filterFields().add(fieldName);
                profile.addRankSetting(fieldName, RankProfile.RankSetting.Type.RANKTYPE, RankType.EMPTY);
            }
        }
    }

    private void filterField(String f) {
        for (RankProfile rp : rankProfileRegistry.localRankProfiles(search)) {
            rp.filterFields().add(f);
        }
    }

    private void findFilterFields(Search search, RankProfile profile, Set<String> filterFields) {
        for (Iterator<RankProfile.RankSetting> itr = profile.declaredRankSettingIterator(); itr.hasNext(); ) {
            RankProfile.RankSetting setting = itr.next();
            if (setting.getType().equals(RankProfile.RankSetting.Type.PREFERBITVECTOR) && ((Boolean)setting.getValue()))
            {
                String fieldName = setting.getFieldName();
                if (search.getConcreteField(fieldName) != null) {
                    if ( ! profile.filterFields().contains(fieldName)) {
                        filterFields.add(fieldName);
                    }
                } else {
                    deployLogger.log(Level.WARNING, "For rank profile '" + profile.getName() + "': Cannot apply rank filter setting to unexisting field '" + fieldName + "'");
                }
            }
        }
    }

}
