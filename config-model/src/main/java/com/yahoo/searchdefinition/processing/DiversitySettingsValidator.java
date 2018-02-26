// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfile;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * @author baldersheim
 */
public class DiversitySettingsValidator extends Processor {

    public DiversitySettingsValidator(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate) {
        if ( ! validate) return;

        for (RankProfile rankProfile : rankProfileRegistry.localRankProfiles(search)) {
            if (rankProfile.getMatchPhaseSettings() != null && rankProfile.getMatchPhaseSettings().getDiversity() != null) {
                validate(rankProfile, rankProfile.getMatchPhaseSettings().getDiversity());
            }
        }
    }
    private void validate(RankProfile rankProfile, RankProfile.DiversitySettings settings) {
        String attributeName = settings.getAttribute();
        new AttributeValidator(search.getName(), rankProfile.getName(),
                               search.getAttribute(attributeName), attributeName).validate();
    }

    private static class AttributeValidator extends  MatchPhaseSettingsValidator.AttributeValidator {

        public AttributeValidator(String searchName, String rankProfileName, Attribute attribute, String attributeName) {
            super(searchName, rankProfileName, attribute, attributeName);
        }

        protected void validateThatAttributeIsSingleAndNotPredicate() {
            if ( ! attribute.getCollectionType().equals(Attribute.CollectionType.SINGLE) ||
                 attribute.getType().equals(Attribute.Type.PREDICATE))
            {
                failValidation("must be single value numeric, or enumerated attribute, but it is '"
                               + attribute.getDataType().getName() + "'");
            }
        }

        @Override
        public void validate() {
            validateThatAttributeExists();
            validateThatAttributeIsSingleAndNotPredicate();
        }

        @Override
        public String getValidationType() {
            return "diversity";
        }

    }

}
