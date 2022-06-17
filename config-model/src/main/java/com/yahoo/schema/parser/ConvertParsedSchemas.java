// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import com.yahoo.schema.DefaultRankProfile;
import com.yahoo.schema.DocumentOnlySchema;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.UnrankedRankProfile;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.document.TemporaryImportedField;
import com.yahoo.schema.parser.ConvertParsedTypes.TypeResolver;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.documentmodel.SummaryField;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    // for unit test
    ConvertParsedSchemas(List<ParsedSchema> orderedInput,
                         DocumentTypeManager documentTypeManager)
    {
        this(orderedInput, documentTypeManager,
             MockApplicationPackage.createEmpty(),
             new MockFileRegistry(),
             new BaseDeployLogger(),
             new TestProperties(),
             new RankProfileRegistry(),
             true);
    }

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
        parsed.getRawAsBase64().ifPresent(value -> schema.enableRawAsBase64(value));
        var typeContext = typeConverter.makeContext(parsed.getDocument());
        var fieldConverter = new ConvertParsedFields(typeContext, convertedStructs);
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
        if (documentsOnly) {
            return; // skip ranking-only content, not used for document type generation
        }
        for (var constant : parsed.getConstants())
            schema.add(constant);
        for (var onnxModel : parsed.getOnnxModels())
            schema.add(onnxModel);
        rankProfileRegistry.add(new DefaultRankProfile(schema, rankProfileRegistry));
        rankProfileRegistry.add(new UnrankedRankProfile(schema, rankProfileRegistry));
        var rankConverter = new ConvertParsedRanking(rankProfileRegistry);
        for (var rankProfile : parsed.getRankProfiles()) {
            rankConverter.convertRankProfile(schema, rankProfile);
        }
    }
}
