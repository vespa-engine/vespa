// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing.multifieldresolver;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.FieldType;
import com.yahoo.search.query.profile.types.QueryProfileType;
import com.yahoo.search.query.profile.types.TensorFieldType;
import com.yahoo.searchdefinition.FeatureNames;
import com.yahoo.searchdefinition.RankProfile;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.searchdefinition.document.ImportedField;
import com.yahoo.searchdefinition.document.ImportedFields;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.processing.Processor;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.util.Map;
import java.util.Optional;

/**
 * Class that processes a search instance and sets type settings on all rank profiles.
 *
 * Currently, type settings are limited to the type of tensor attribute fields and tensor query features.
 *
 * @author geirst
 */
public class RankProfileTypeSettingsProcessor extends Processor {

    public RankProfileTypeSettingsProcessor(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate) {
        processAttributeFields();
        processImportedFields();
        processQueryProfileTypes();
    }

    private void processAttributeFields() {
        for (SDField field : search.allConcreteFields()) {
            Attribute attribute = field.getAttributes().get(field.getName());
            if (attribute != null && attribute.tensorType().isPresent()) {
                addAttributeTypeToRankProfiles(attribute.getName(), attribute.tensorType().get().toString());
            }
        }
    }

    private void processImportedFields() {
        Optional<ImportedFields> importedFields = search.importedFields();
        if (importedFields.isPresent()) {
            importedFields.get().fields().forEach((fieldName, field) -> processImportedField(field));
        }
    }

    private void processImportedField(ImportedField field) {
        SDField targetField = field.targetField();
        Attribute attribute = targetField.getAttributes().get(targetField.getName());
        if (attribute != null && attribute.tensorType().isPresent()) {
            addAttributeTypeToRankProfiles(field.fieldName(), attribute.tensorType().get().toString());
        }
    }

    private void addAttributeTypeToRankProfiles(String attributeName, String attributeType) {
        for (RankProfile profile : rankProfileRegistry.allRankProfiles()) {
            profile.addAttributeType(attributeName, attributeType);
        }
    }

    private void processQueryProfileTypes() {
        for (QueryProfileType queryProfileType : queryProfiles.getRegistry().getTypeRegistry().allComponents()) {
            for (Map.Entry<String, FieldDescription> fieldDescEntry : queryProfileType.fields().entrySet()) {
                processFieldDescription(fieldDescEntry.getValue());
            }
        }
    }

    private void processFieldDescription(FieldDescription fieldDescription) {
        String fieldName = fieldDescription.getName();
        FieldType fieldType = fieldDescription.getType();
        if (fieldType instanceof TensorFieldType) {
            TensorFieldType tensorFieldType = (TensorFieldType)fieldType;
            FeatureNames.argumentOf(fieldName).ifPresent(argument ->
                addQueryFeatureTypeToRankProfiles(argument, tensorFieldType.asTensorType().toString()));
        }
    }

    private void addQueryFeatureTypeToRankProfiles(String queryFeature, String queryFeatureType) {
        for (RankProfile profile : rankProfileRegistry.allRankProfiles()) {
            profile.addQueryFeatureType(queryFeature, queryFeatureType);
        }
    }

}
