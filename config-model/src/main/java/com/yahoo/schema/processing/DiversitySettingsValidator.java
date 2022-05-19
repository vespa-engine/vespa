// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.RankProfile;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.Attribute;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * @author baldersheim
 */
public class DiversitySettingsValidator extends Processor {

    public DiversitySettingsValidator(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        if ( ! validate) return;
        if (documentsOnly) return;

        for (RankProfile rankProfile : rankProfileRegistry.rankProfilesOf(schema)) {
            if (rankProfile.getMatchPhaseSettings() != null && rankProfile.getMatchPhaseSettings().getDiversity() != null) {
                validate(rankProfile, rankProfile.getMatchPhaseSettings().getDiversity());
            }
        }
    }
    private void validate(RankProfile rankProfile, RankProfile.DiversitySettings settings) {
        String attributeName = settings.getAttribute();
        new AttributeValidator(schema.getName(), rankProfile.name(),
                               schema.getAttribute(attributeName), attributeName).validate();
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
