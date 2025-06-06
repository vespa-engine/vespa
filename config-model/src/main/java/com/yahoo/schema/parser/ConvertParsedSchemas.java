// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.parser;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.document.DataType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.PositionDataType;
import com.yahoo.schema.DefaultRankProfile;
import com.yahoo.schema.DocumentOnlySchema;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.UnrankedRankProfile;
import com.yahoo.schema.derived.SummaryClass;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.document.TemporaryImportedField;
import com.yahoo.schema.parser.ConvertParsedTypes.TypeResolver;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.documentmodel.SummaryField;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.Map;
import java.util.Optional;

/**
 * Class converting a collection of schemas from the intermediate format.
 *
 * @author arnej27959
 **/
public class ConvertParsedSchemas {

    private final List<ParsedSchema> orderedInput;
    private final DocumentTypeManager docMan;
    private final ApplicationPackage applicationPackage;
    private final FileRegistry fileRegistry;
    private final DeployLogger deployLogger;
    private final ModelContext.Properties properties;
    private final RankProfileRegistry rankProfileRegistry;
    private final boolean documentsOnly;
    private final ConvertParsedTypes typeConverter;

    public ConvertParsedSchemas(List<ParsedSchema> orderedInput,
                                DocumentTypeManager documentTypeManager,
                                ApplicationPackage applicationPackage,
                                FileRegistry fileRegistry,
                                DeployLogger deployLogger,
                                ModelContext.Properties properties,
                                RankProfileRegistry rankProfileRegistry,
                                boolean documentsOnly)
    {
        this.orderedInput = orderedInput;
        this.docMan = documentTypeManager;
        this.applicationPackage = applicationPackage;
        this.fileRegistry = fileRegistry;
        this.deployLogger = deployLogger;
        this.properties = properties;
        this.rankProfileRegistry = rankProfileRegistry;
        this.documentsOnly = documentsOnly;
        this.typeConverter = new ConvertParsedTypes(orderedInput, docMan);
    }

    private final Map<String, SDDocumentType> convertedDocuments = new LinkedHashMap<>();
    private final Map<String, SDDocumentType> convertedStructs = new LinkedHashMap<>();

    public List<Schema> convertToSchemas() {
        typeConverter.convert(false);
        var resultList = new ArrayList<Schema>();
        for (var parsed : orderedInput) {
            Optional<String> inherited;
            var inheritList = parsed.getInherited();
            if (inheritList.size() == 0) {
                inherited = Optional.empty();
            } else if (inheritList.size() == 1) {
                inherited = Optional.of(inheritList.get(0));
            } else {
                throw new IllegalArgumentException("schema " + parsed.name() + "cannot inherit more than once");
            }
            Schema schema = parsed.getDocumentWithoutSchema()
                ? new DocumentOnlySchema(applicationPackage, fileRegistry, deployLogger, properties)
                : new Schema(parsed.name(), applicationPackage, inherited, fileRegistry, deployLogger, properties);
            inherited.ifPresent(parentName -> {
                    for (var possibleParent : resultList) {
                        if (possibleParent.getName().equals(parentName)) {
                            schema.setInheritedSchema(possibleParent);
                        }
                    }
                });
            convertSchema(schema, parsed);
            resultList.add(schema);
        }
        return resultList;
    }

    private void convertDocument(Schema schema, ParsedDocument parsed,
                                 ConvertParsedFields fieldConverter)
    {
        SDDocumentType document = new SDDocumentType(parsed.name());
        for (var struct : parsed.getStructs()) {
            var structProxy = fieldConverter.convertStructDeclaration(schema, document, struct);
            document.addType(structProxy);
        }
        for (String inherit : parsed.getInherited()) {
            var parent = convertedDocuments.get(inherit);
            assert(parent != null);
            document.inherit(parent);
        }
        for (var annotation : parsed.getAnnotations()) {
            fieldConverter.convertAnnotation(schema, document, annotation);
        }
        for (var field : parsed.getFields()) {
            var sdf = fieldConverter.convertDocumentField(schema, document, field);
            if (field.hasIdOverride()) {
                document.setFieldId(sdf, field.idOverride());
            }
        }
        convertedDocuments.put(parsed.name(), document);
        schema.addDocument(document);
    }

    /*
     * Helper class for resolving data type for a document summary. Summary type is still
     * used internally in config model when generating and processing indexing scripts.
     * See DynamicSummaryTransformUtils class comment for more details.
     *
     * This kind of resolving is a temporary measure until the use of summary fields have
     * been eliminated from indexing scripts and are no longer used to extend the document
     * type. At that time, the data type of a summary field is no longer relevant.
     */
    private class SummaryFieldTypeResolver {

        private final Schema schema;
        private final Map<String, ParsedSummaryField> summaryFields = new LinkedHashMap<String, ParsedSummaryField>();
        private static final String zCurveSuffix = new String("_zcurve");

        public SummaryFieldTypeResolver(Schema schema, List<ParsedDocumentSummary> parsed) {
            this.schema = schema;
            for (var docsum : parsed) {
                for (var field : docsum.getSummaryFields()) {
                    summaryFields.put(field.name(), field);
                }
            }
        }

        private boolean isPositionAttribute(Schema schema, String sourceFieldName) {
            if (!sourceFieldName.endsWith(zCurveSuffix)) {
                return false;
            }
            var name = sourceFieldName.substring(0, sourceFieldName.length() - zCurveSuffix.length());
            var field = schema.getField(name);
            return (field.getDataType().equals(PositionDataType.INSTANCE));
        }


        private String getSingleSource(ParsedSummaryField parsedField) {
            if (parsedField.getSources().size() == 1) {
                return parsedField.getSources().get(0);
            }
            return parsedField.name();
        }

        public DataType resolve(ParsedDocumentSummary docsum, ParsedSummaryField parsedField) {
            var seen = new LinkedHashSet<String>();
            var origName = parsedField.name();
            while (true) {
                if (seen.contains(parsedField.name())) {
                    throw new IllegalArgumentException("For schema '" + schema.getName() +
                            "' document-summary '" + docsum.name() +
                            "' summary field '" + origName +
                            "': Source loop detected for summary field '" + parsedField.name() + "'");
                }
                seen.add(parsedField.name());
                if (parsedField.getSources().size() >= 2) {
                    return DataType.STRING; // Flattening, streaming search
                }
                var source = getSingleSource(parsedField);
                if (source.equals(SummaryClass.DOCUMENT_ID_FIELD)) {
                    return DataType.STRING; // Reserved source field name
                } else if (isPositionAttribute(schema, source)) {
                    return DataType.LONG;   // Extra field with suffix is added later for positions
                }
                var field = schema.getField(source);
                if (field != null) {
                    return field.getDataType();
                } else if (schema.temporaryImportedFields().isPresent() &&
                        schema.temporaryImportedFields().get().hasField(source)) {
                    return null; // Imported field, cannot resolve now
                } else if (source.equals(parsedField.name()) || !summaryFields.containsKey(source)) {
                    throw new IllegalArgumentException("For schema '" + schema.getName() +
                            "', document-summary '" + docsum.name() +
                            "', summary field '" + parsedField.name() +
                            "': there is no valid source '" + source + "'.");
                }
                parsedField = summaryFields.get(source);
            }
        }
    }

    private void convertDocumentSummary(Schema schema, ParsedDocumentSummary parsed, TypeResolver typeContext,
                                        SummaryFieldTypeResolver sfResolver) {
        var docsum = new DocumentSummary(parsed.name(), schema);
        parsed.getInherited().forEach(inherited -> docsum.addInherited(inherited));
        if (parsed.getFromDisk()) {
            docsum.setFromDisk(true);
        }
        if (parsed.getOmitSummaryFeatures()) {
            docsum.setOmitSummaryFeatures(true);
        }
        for (var parsedField : parsed.getSummaryFields()) {
            var parsedType = parsedField.getType();
            if (parsedType != null) {
                var log = schema.getDeployLogger();
                log.log(Level.WARNING, () -> "For schema '" + schema.getName() +
                        "', document-summary '" + parsed.name() +
                        "', summary field '" + parsedField.name() +
                        "': Specifying the type is deprecated, ignored and will be an error in Vespa 9." +
                        " Remove the type specification to silence this warning.");
            }
            DataType dataType = (parsedType != null) ? typeContext.resolveType(parsedType) : null;
            DataType existingType = sfResolver.resolve(parsed, parsedField);
            if (existingType != null) {
                if (dataType == null) {
                    dataType = existingType;
                } else if (!dataType.equals(existingType)) {
                    if (dataType.getValueClass().equals(com.yahoo.document.datatypes.WeightedSet.class)) {
                        // "adjusting type for field " + parsedField.name() + " in document-summary " + parsed.name() + " field already has: " + existingType + " but declared type was: " + dataType
                        dataType = existingType;
                    }
                }
            }
            var summaryField = (dataType == null) ?
                    SummaryField.createWithUnresolvedType(parsedField.name(), docsum) :
                    new SummaryField(parsedField.name(), dataType, docsum);
            // XXX does not belong here:
            summaryField.setVsmCommand(SummaryField.VsmCommand.FLATTENSPACE);
            ConvertParsedFields.convertSummaryFieldSettings(schema, summaryField, parsedField, parsed.name());
            docsum.add(summaryField);
        }
        schema.addSummary(docsum);
    }

    private void convertImportField(Schema schema, ParsedSchema.ImportedField f) {
        // needs rethinking
        var importedFields = schema.temporaryImportedFields().get();
        if (importedFields.hasField(f.asFieldName)) {
            throw new IllegalArgumentException("For schema '" + schema.getName() +
                                               "', import field as '" + f.asFieldName +
                                               "': Field already imported");
        }
        importedFields.add(new TemporaryImportedField(f.asFieldName, f.refFieldName, f.foreignFieldName));
    }

    private void convertFieldSet(Schema schema, ParsedFieldSet parsed) {
        String setName = parsed.name();
        for (String field : parsed.getFieldNames()) {
            schema.fieldSets().addUserFieldSetItem(setName, field);
        }
        for (String command : parsed.getQueryCommands()) {
            schema.fieldSets().userFieldSets().get(setName).queryCommands().add(command);
        }
        if (parsed.getMatchSettings().isPresent()) {
            // same ugliness as SDParser.jj used to have:
            var tmp = new SDField(setName, DataType.STRING);
            ConvertParsedFields.convertMatchSettings(tmp, parsed.matchSettings());
            schema.fieldSets().userFieldSets().get(setName).setMatching(tmp.getMatching());
        }
    }

    private void convertSchema(Schema schema, ParsedSchema parsed) {
        if (parsed.hasStemming()) {
            schema.setStemming(parsed.getStemming());
        }
        parsed.getRawAsBase64().ifPresent(value -> schema.enableRawAsBase64(value));
        var typeContext = typeConverter.makeContext(parsed.getDocument());
        var sfResolver = new SummaryFieldTypeResolver(schema, parsed.getDocumentSummaries());
        var fieldConverter = new ConvertParsedFields(typeContext, convertedStructs);
        convertDocument(schema, parsed.getDocument(), fieldConverter);
        for (var field : parsed.getFields()) {
            fieldConverter.convertExtraField(schema, field);
        }
        for (var index : parsed.getIndexes()) {
            fieldConverter.convertExtraIndex(schema, index);
        }
        for (var importedField : parsed.getImportedFields()) {
            convertImportField(schema, importedField);
        }
        for (var docsum : parsed.getDocumentSummaries()) {
            convertDocumentSummary(schema, docsum, typeContext, sfResolver);
        }
        for (var fieldSet : parsed.getFieldSets()) {
            convertFieldSet(schema, fieldSet);
        }
        if (documentsOnly) {
            return; // skip ranking-only content, not used for document type generation
        }
        for (var constant : parsed.getConstants())
            schema.add(constant);
        for (var onnxModel : parsed.getOnnxModels())
            schema.add(onnxModel);
        rankProfileRegistry.add(new DefaultRankProfile(schema, rankProfileRegistry));
        rankProfileRegistry.add(new UnrankedRankProfile(schema, rankProfileRegistry));
        var rankConverter = new ParsedRankingConverter(rankProfileRegistry);
        for (var rankProfile : parsed.getRankProfiles()) {
            rankConverter.convertRankProfile(schema, rankProfile);
        }
    }
}
