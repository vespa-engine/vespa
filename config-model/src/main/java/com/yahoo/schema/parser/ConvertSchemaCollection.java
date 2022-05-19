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
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;

import java.util.ArrayList;
import java.util.List;

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
        resolveStructInheritance();
        resolveAnnotationInheritance();
        addMissingAnnotationStructs();
        var converter = new ConvertParsedSchemas(orderedInput,
                                                 docMan,
                                                 applicationPackage,
                                                 fileRegistry,
                                                 deployLogger,
                                                 properties,
                                                 rankProfileRegistry,
                                                 documentsOnly);
        return converter.convertToSchemas();
    }

    private void resolveStructInheritance() {
        List<ParsedStruct> all = new ArrayList<>();
        for (var schema : orderedInput) {
            var doc = schema.getDocument();
            for (var struct : doc.getStructs()) {
                for (String inherit : struct.getInherited()) {
                    var parent = doc.findParsedStruct(inherit);
                    if (parent == null) {
                        throw new IllegalArgumentException("Can not find parent for "+struct+" in "+doc);
                    }
                    struct.resolveInherit(inherit, parent);
                }
                all.add(struct);
            }
        }
        List<String> seen = new ArrayList<>();
        for (ParsedStruct struct : all) {
            inheritanceCycleCheck(struct, seen);
        }
    }

    private void resolveAnnotationInheritance() {
        List<ParsedAnnotation> all = new ArrayList();
        for (var schema : orderedInput) {
            var doc = schema.getDocument();
            for (var annotation : doc.getAnnotations()) {
                for (String inherit : annotation.getInherited()) {
                    var parent = doc.findParsedAnnotation(inherit);
                    if (parent == null) {
                        throw new IllegalArgumentException("Can not find parent for "+annotation+" in "+doc);
                    }
                    annotation.resolveInherit(inherit, parent);
                }
                all.add(annotation);
            }
        }
        List<String> seen = new ArrayList<>();
        for (ParsedAnnotation annotation : all) {
            inheritanceCycleCheck(annotation, seen);
        }
    }

    private void fixupAnnotationStruct(ParsedAnnotation parsed) {
        for (var parent : parsed.getResolvedInherits()) {
            fixupAnnotationStruct(parent);
            parent.getStruct().ifPresent(ps -> {
                    var myStruct = parsed.ensureStruct();
                    if (! myStruct.getInherited().contains(ps.name())) {
                        myStruct.inherit(ps.name());
                        myStruct.resolveInherit(ps.name(), ps);
                    }
                });
        }
    }

    private void addMissingAnnotationStructs() {
        for (var schema : orderedInput) {
            var doc = schema.getDocument();
            for (var annotation : doc.getAnnotations()) {
                fixupAnnotationStruct(annotation);
            }
        }
    }

    private void inheritanceCycleCheck(ParsedStruct struct, List<String> seen) {
        String name = struct.name();
        if (seen.contains(name)) {
            seen.add(name);
            throw new IllegalArgumentException("Inheritance/reference cycle for structs: " +
                                               String.join(" -> ", seen));
        }
        seen.add(name);
        for (ParsedStruct parent : struct.getResolvedInherits()) {
            inheritanceCycleCheck(parent, seen);
        }
        seen.remove(name);
    }

    private void inheritanceCycleCheck(ParsedAnnotation annotation, List<String> seen) {
        String name = annotation.name();
        if (seen.contains(name)) {
            seen.add(name);
            throw new IllegalArgumentException("Inheritance/reference cycle for annotations: " +
                                               String.join(" -> ", seen));
        }
        seen.add(name);
        for (ParsedAnnotation parent : annotation.getResolvedInherits()) {
            inheritanceCycleCheck(parent, seen);
        }
        seen.remove(name);
    }

}
