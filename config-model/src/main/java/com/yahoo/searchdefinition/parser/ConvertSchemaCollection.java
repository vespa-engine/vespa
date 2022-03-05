// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.parser;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.document.DataType;
import com.yahoo.document.DataTypeName;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.PositionDataType;
import com.yahoo.document.ReferenceDataType;
import com.yahoo.document.StructDataType;
import com.yahoo.document.WeightedSetDataType;
import com.yahoo.document.annotation.AnnotationReferenceDataType;
import com.yahoo.document.annotation.AnnotationType;
import com.yahoo.searchdefinition.DefaultRankProfile;
import com.yahoo.searchdefinition.DocumentOnlySchema;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Schema;
import com.yahoo.searchdefinition.UnrankedRankProfile;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.document.TemporaryImportedField;
import com.yahoo.searchdefinition.document.annotation.SDAnnotationType;
import com.yahoo.searchdefinition.parser.ConvertParsedTypes.TypeResolver;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.documentmodel.SummaryField;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Class converting a collection of schemas from the intermediate format.
 *
 * @author arnej27959
 **/
public class ConvertSchemaCollection {

    private final IntermediateCollection input;
    private final List<ParsedSchema> orderedInput = new ArrayList<>();
    private final DocumentTypeManager docMan;
    private final ApplicationPackage applicationPackage;
    private final FileRegistry fileRegistry;
    private final DeployLogger deployLogger;
    private final ModelContext.Properties properties;
    private final RankProfileRegistry rankProfileRegistry;
    private final boolean documentsOnly;

    // for unit test
    ConvertSchemaCollection(IntermediateCollection input,
                            DocumentTypeManager documentTypeManager)
    {
        this(input, documentTypeManager,
             MockApplicationPackage.createEmpty(),
             new MockFileRegistry(),
             new BaseDeployLogger(),
             new TestProperties(),
             new RankProfileRegistry(),
             true);
    }

    public ConvertSchemaCollection(IntermediateCollection input,
                                   DocumentTypeManager documentTypeManager,
                                   ApplicationPackage applicationPackage,
                                   FileRegistry fileRegistry,
                                   DeployLogger deployLogger,
                                   ModelContext.Properties properties,
                                   RankProfileRegistry rankProfileRegistry,
                                   boolean documentsOnly)
    {
        this.input = input;
        this.docMan = documentTypeManager;
        this.applicationPackage = applicationPackage;
        this.fileRegistry = fileRegistry;
        this.deployLogger = deployLogger;
        this.properties = properties;
        this.rankProfileRegistry = rankProfileRegistry;
        this.documentsOnly = documentsOnly;

        input.resolveInternalConnections();
        order();
        pushTypesToDocuments();
    }

    void order() {
        var map = input.getParsedSchemas();
        for (var schema : map.values()) {
            findOrdering(schema);
        }
    }

    void findOrdering(ParsedSchema schema) {
        if (orderedInput.contains(schema)) return;
        for (var parent : schema.getAllResolvedInherits()) {
            findOrdering(parent);
        }
        orderedInput.add(schema);
    }

    void pushTypesToDocuments() {
        for (var schema : orderedInput) {
            for (var struct : schema.getStructs()) {
                schema.getDocument().addStruct(struct);
            }
            for (var annotation : schema.getAnnotations()) {
                schema.getDocument().addAnnotation(annotation);
            }
        }
    }

    private ConvertParsedTypes typeConverter;

    public void convertTypes() {
        typeConverter = new ConvertParsedTypes(orderedInput, docMan);
        typeConverter.convert(true);
    }

    public List<Schema> convertToSchemas() {
        typeConverter = new ConvertParsedTypes(orderedInput, docMan);
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
            convertSchema(schema, parsed);
            resultList.add(schema);
        }
        return resultList;
    }

    private void convertAnnotation(Schema schema, SDDocumentType document, ParsedAnnotation parsed, ConvertParsedFields fieldConverter) {
        var type = new SDAnnotationType(parsed.name());
        for (String inherit : parsed.getInherited()) {
            type.inherit(inherit);
        }
        var payload = parsed.getStruct();
        if (payload.isPresent()) {
            var struct = fieldConverter.convertStructDeclaration(schema, payload.get());
            type = new SDAnnotationType(parsed.name(), struct, type.getInherits());
            // WTF?
            struct.setStruct(null);
        }
        document.addAnnotation(type);
    }

    private void convertDocument(Schema schema, ParsedDocument parsed,
                                 ConvertParsedFields fieldConverter)
    {
        SDDocumentType document = new SDDocumentType(parsed.name());
        for (String inherit : parsed.getInherited()) {
            document.inherit(new DataTypeName(inherit));
        }
        for (var struct : parsed.getStructs()) {
            var structProxy = fieldConverter.convertStructDeclaration(schema, struct);
            document.addType(structProxy);
        }
        for (var annotation : parsed.getAnnotations()) {
            convertAnnotation(schema, document, annotation, fieldConverter);
        }
        for (var field : parsed.getFields()) {
            var sdf = fieldConverter.convertDocumentField(schema, document, field);
            if (field.hasIdOverride()) {
                document.setFieldId(sdf, field.idOverride());
            }
        }
        schema.addDocument(document);
    }

    private void convertDocumentSummary(Schema schema, ParsedDocumentSummary parsed, TypeResolver typeContext) {
        var docsum = new DocumentSummary(parsed.name(), schema);
        var inheritList = parsed.getInherited();
        if (inheritList.size() == 1) {
            docsum.setInherited(inheritList.get(0));
        } else if (inheritList.size() != 0) {
            throw new IllegalArgumentException("document-summary "+parsed.name()+" cannot inherit more than once");
        }
        if (parsed.getFromDisk()) {
            docsum.setFromDisk(true);
        }
        if (parsed.getOmitSummaryFeatures()) {
            docsum.setOmitSummaryFeatures(true);
        }
        for (var parsedField : parsed.getSummaryFields()) {
            DataType dataType = typeContext.resolveType(parsedField.getType());
            var summaryField = new SummaryField(parsedField.name(), dataType);
            // XXX does not belong here:
            summaryField.setVsmCommand(SummaryField.VsmCommand.FLATTENSPACE);
            ConvertParsedFields.convertSummaryFieldSettings(summaryField, parsedField);
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
        schema.enableRawAsBase64(parsed.getRawAsBase64());
        var typeContext = typeConverter.makeContext(parsed.getDocument());
        var fieldConverter = new ConvertParsedFields(typeContext);
        convertDocument(schema, parsed.getDocument(), fieldConverter);
        for (var field : parsed.getFields()) {
            fieldConverter.convertExtraField(schema, field);
        }
        for (var index : parsed.getIndexes()) {
            fieldConverter.convertExtraIndex(schema, index);
        }
        for (var docsum : parsed.getDocumentSummaries()) {
            convertDocumentSummary(schema, docsum, typeContext);
        }
        for (var importedField : parsed.getImportedFields()) {
            convertImportField(schema, importedField);
        }
        for (var fieldSet : parsed.getFieldSets()) {
            convertFieldSet(schema, fieldSet);
        }
        for (var rankingConstant : parsed.getRankingConstants()) {
            schema.rankingConstants().add(rankingConstant);
        }
        for (var onnxModel : parsed.getOnnxModels()) {
            schema.onnxModels().add(onnxModel);
        }
        rankProfileRegistry.add(new DefaultRankProfile(schema, rankProfileRegistry, schema.rankingConstants()));
        rankProfileRegistry.add(new UnrankedRankProfile(schema, rankProfileRegistry, schema.rankingConstants()));
        var rankConverter = new ConvertParsedRanking(rankProfileRegistry);
        for (var rankProfile : parsed.getRankProfiles()) {
            rankConverter.convertRankProfile(schema, rankProfile);
        }
    }

}
