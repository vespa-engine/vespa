// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing.multifieldresolver;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.FieldType;
import com.yahoo.search.query.profile.types.QueryProfileType;
import com.yahoo.search.query.profile.types.TensorFieldType;
import com.yahoo.schema.FeatureNames;
import com.yahoo.schema.RankProfile;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.Attribute;
import com.yahoo.schema.document.ImmutableSDField;
import com.yahoo.schema.document.ImportedField;
import com.yahoo.schema.document.ImportedFields;
import com.yahoo.schema.processing.Processor;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.tensor.TensorType;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.util.Map;
import java.util.Optional;

/**
 * This processes a schema and adds input type settings on all rank profiles.
 *
 * Currently, type settings are limited to the type of tensor attribute fields and tensor query features.
 *
 * @author geirst
 */
public class RankProfileTypeSettingsProcessor extends Processor {

    public RankProfileTypeSettingsProcessor(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        if (documentsOnly) return;

        processAttributeFields();
        processImportedFields();
        processQueryProfileTypes();
    }

    private void processAttributeFields() {
        if (schema == null) return; // we're processing global profiles
        for (ImmutableSDField field : schema.allConcreteFields()) {
            Attribute attribute = field.getAttributes().get(field.getName());
            if (attribute != null && attribute.tensorType().isPresent()) {
                addAttributeTypeToRankProfiles(attribute.getName(), attribute.tensorType().get().toString());
            }
        }
    }

    private void processImportedFields() {
        if (schema == null) return; // we're processing global profiles
        Optional<ImportedFields> importedFields = schema.importedFields();
        if (importedFields.isPresent()) {
            importedFields.get().fields().forEach((fieldName, field) -> processImportedField(field));
        }
    }

    private void processImportedField(ImportedField field) {
        ImmutableSDField targetField = field.targetField();
        Attribute attribute = targetField.getAttributes().get(targetField.getName());
        if (attribute != null && attribute.tensorType().isPresent()) {
            addAttributeTypeToRankProfiles(field.fieldName(), attribute.tensorType().get().toString());
        }
    }

    private void addAttributeTypeToRankProfiles(String attributeName, String attributeType) {
        for (RankProfile profile : rankProfileRegistry.rankProfilesOf(schema)) {
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
        FieldType fieldType = fieldDescription.getType();
        if (fieldType instanceof TensorFieldType) {
            TensorFieldType tensorFieldType = (TensorFieldType)fieldType;
            Optional<Reference> reference = Reference.simple(fieldDescription.getName());
            if (reference.isPresent() && FeatureNames.isQueryFeature(reference.get()))
                addQueryFeatureTypeToRankProfiles(reference.get(), tensorFieldType.asTensorType());
        }
    }

    private void addQueryFeatureTypeToRankProfiles(Reference queryFeature, TensorType queryFeatureType) {
        for (RankProfile profile : rankProfileRegistry.all()) {
            if (! profile.inputs().containsKey(queryFeature)) // declared inputs have precedence
                profile.addInput(queryFeature,
                                 new RankProfile.Input(queryFeature, queryFeatureType, Optional.empty()));
        }
    }

}
