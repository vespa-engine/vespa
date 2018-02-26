// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.document.DataType;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.Search;
import com.yahoo.vespa.indexinglanguage.expressions.SetLanguageExpression;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.util.ArrayList;
import java.util.List;

/**
 * Check that no text field appears before a field that sets language.
 *
 * @author Gunnar Gauslaa Bergem
 */
public class SetLanguage extends Processor {

    public SetLanguage(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate) {
        List<String> textFieldsWithoutLanguage = new ArrayList<>();

        for (SDField field : search.allConcreteFields()) {
            if (fieldMustComeAfterLanguageSettingField(field)) {
                textFieldsWithoutLanguage.add(field.getName());
            }
            if (field.containsExpression(SetLanguageExpression.class) && !textFieldsWithoutLanguage.isEmpty()) {
                StringBuilder fieldString = new StringBuilder();
                for (String fieldName : textFieldsWithoutLanguage) {
                    fieldString.append(fieldName).append(" ");
                }
                warn(search, field, "Field '" + field.getName() + "' sets the language for this document, " +
                                    "and should be defined as the first field in the searchdefinition." +
                                    "Preceding text fields that will not have their language set: " +
                                    fieldString.toString() +
                                    " (This warning is omitted for any subsequent fields that also do set_language.)");
                return;
            }
        }
    }

    private boolean fieldMustComeAfterLanguageSettingField(SDField field) {
        return (!field.containsExpression(SetLanguageExpression.class) &&
                (field.getDataType() == DataType.STRING));
    }

}
