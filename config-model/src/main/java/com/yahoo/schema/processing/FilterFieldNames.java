// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.RankType;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.RankProfile;
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

    public FilterFieldNames(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        if (documentsOnly) return;

        for (SDField f : schema.allConcreteFields()) {
            if (f.getRanking().isFilter()) {
                filterField(f.getName());
            }
        }

        for (RankProfile profile : rankProfileRegistry.rankProfilesOf(schema)) {
            Set<String> filterFields = new LinkedHashSet<>();
            findFilterFields(schema, profile, filterFields);
            for (Iterator<String> itr = filterFields.iterator(); itr.hasNext(); ) {
                String fieldName = itr.next();
                profile.filterFields().add(fieldName);
                profile.addRankSetting(fieldName, RankProfile.RankSetting.Type.RANKTYPE, RankType.EMPTY);
            }
        }
    }

    private void filterField(String f) {
        for (RankProfile rp : rankProfileRegistry.rankProfilesOf(schema)) {
            rp.filterFields().add(f);
        }
    }

    private void findFilterFields(Schema schema, RankProfile profile, Set<String> filterFields) {
        for (Iterator<RankProfile.RankSetting> itr = profile.declaredRankSettingIterator(); itr.hasNext(); ) {
            RankProfile.RankSetting setting = itr.next();
            if (setting.getType().equals(RankProfile.RankSetting.Type.PREFERBITVECTOR) && ((Boolean)setting.getValue()))
            {
                String fieldName = setting.getFieldName();
                if (schema.getConcreteField(fieldName) != null) {
                    if ( ! profile.filterFields().contains(fieldName)) {
                        filterFields.add(fieldName);
                    }
                } else {
                    deployLogger.logApplicationPackage(Level.WARNING, "For rank profile '" + profile.name() + "': Cannot apply rank filter setting to unexisting field '" + fieldName + "'");
                }
            }
        }
    }

}
