// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.compress.CompressionType;
import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.document.annotation.AnnotationReferenceDataType;
import com.yahoo.document.annotation.AnnotationType;
import com.yahoo.log.LogLevel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Configures the Vespa document manager from a config id.
 *
 * @author Einar M R Rosenvinge
 */
public class DocumentTypeManagerConfigurer implements ConfigSubscriber.SingleSubscriber<DocumentmanagerConfig>{

    private final static Logger log = Logger.getLogger(DocumentTypeManagerConfigurer.class.getName());

    private DocumentTypeManager managerToConfigure;

    public DocumentTypeManagerConfigurer(DocumentTypeManager manager) {
        this.managerToConfigure = manager;
    }

    private static CompressionConfig makeCompressionConfig(DocumentmanagerConfig.Datatype.Structtype cfg) {
        return new CompressionConfig(toCompressorType(cfg.compresstype()), cfg.compresslevel(),
                                     cfg.compressthreshold(), cfg.compressminsize());
    }

    public static CompressionType toCompressorType(DocumentmanagerConfig.Datatype.Structtype.Compresstype.Enum value) {
        switch (value) {
            case NONE: return CompressionType.NONE;
            case LZ4: return CompressionType.LZ4;
            case UNCOMPRESSABLE: return CompressionType.INCOMPRESSIBLE;
        }
        throw new IllegalArgumentException("Compression type " + value + " is not supported");
    }

    /**
     * <p>Makes the DocumentTypeManager subscribe on its config.</p>
     *
     * <p>Proper Vespa setups will use a config id which looks up the document manager config
     * at the document server, but it is also possible to read config from a file containing
     * a document manager configuration by using
     * <code>file:path-to-document-manager.cfg</code>.</p>
     *
     * @param configId the config ID to use
     */
    public static ConfigSubscriber configure(DocumentTypeManager manager, String configId) {
        return new DocumentTypeManagerConfigurer(manager).configure(configId);
    }

    public ConfigSubscriber configure(String configId) {
        ConfigSubscriber subscriber = new ConfigSubscriber();
        subscriber.subscribe(this, DocumentmanagerConfig.class, configId);
        return subscriber;
    }

    static void configureNewManager(DocumentmanagerConfig config, DocumentTypeManager manager) {
        if (config == null) {
            return;
        }

        setupAnnotationTypesWithoutPayloads(config, manager);
        setupAnnotationRefTypes(config, manager);

        log.log(LogLevel.DEBUG, "Configuring document manager with " + config.datatype().size() + " data types.");
        ArrayList<DocumentmanagerConfig.Datatype> failed = new ArrayList<>();
        failed.addAll(config.datatype());
        while (!failed.isEmpty()) {
            ArrayList<DocumentmanagerConfig.Datatype> tmp = failed;
            failed = new ArrayList<>();
            for (int i = 0; i < tmp.size(); i++) {
                DocumentmanagerConfig.Datatype thisDataType = tmp.get(i);
                int id = thisDataType.id();
                try {
                    registerTypeIdMapping(config, manager, thisDataType, id);
                } catch (IllegalArgumentException e) {
                    failed.add(thisDataType);
                }
            }
        }
        addStructInheritance(config, manager);
        addAnnotationTypePayloads(config, manager);
        addAnnotationTypeInheritance(config, manager);

        manager.replaceTemporaryTypes();
    }

    private static void registerTypeIdMapping(DocumentmanagerConfig config, DocumentTypeManager manager, DocumentmanagerConfig.Datatype thisDataType, int id) {
        for (Object o : thisDataType.arraytype()) {
            registerArrayType(manager, id, (DocumentmanagerConfig.Datatype.Arraytype) o);
        }
        for (Object o : thisDataType.maptype()) {
            registerMapType(manager, id, (DocumentmanagerConfig.Datatype.Maptype) o);
        }
        for (Object o : thisDataType.weightedsettype()) {
            registerWeightedSetType(manager, id, (DocumentmanagerConfig.Datatype.Weightedsettype) o);
        }
        for (Object o : thisDataType.structtype()) {
            registerStructType(config, manager, id, (DocumentmanagerConfig.Datatype.Structtype) o);
        }
        for (Object o : thisDataType.documenttype()) {
            registerDocumentType(manager, (DocumentmanagerConfig.Datatype.Documenttype) o);
        }
        for (Object o : thisDataType.referencetype()) {
            registerReferenceType(manager, id, (DocumentmanagerConfig.Datatype.Referencetype) o);
        }
    }

    private static void registerArrayType(DocumentTypeManager manager, int id,
                                          DocumentmanagerConfig.Datatype.Arraytype array) {
        DataType nestedType = manager.getDataType(array.datatype(), "");
        ArrayDataType type = new ArrayDataType(nestedType, id);
        manager.register(type);
    }

    private static void registerMapType(DocumentTypeManager manager, int id,
                                        DocumentmanagerConfig.Datatype.Maptype map) {
        DataType keyType = manager.getDataType(map.keytype(), "");
        DataType valType = manager.getDataType(map.valtype(), "");
        MapDataType type = new MapDataType(keyType, valType, id);
        manager.register(type);
    }

    private static void registerWeightedSetType(DocumentTypeManager manager, int id,
                                                DocumentmanagerConfig.Datatype.Weightedsettype wset) {
        DataType nestedType = manager.getDataType(wset.datatype(), "");
        WeightedSetDataType type = new WeightedSetDataType(
                nestedType, wset.createifnonexistant(), wset.removeifzero(), id);
        manager.register(type);
    }

    @SuppressWarnings("deprecation")
    private static void registerDocumentType(DocumentTypeManager manager, DocumentmanagerConfig.Datatype.Documenttype doc) {
        StructDataType header = (StructDataType) manager.getDataType(doc.headerstruct(), "");
        StructDataType body = (StructDataType) manager.getDataType(doc.bodystruct(), "");
        for (Field field : body.getFields()) {
            field.setHeader(false);
        }
        DocumentType type = new DocumentType(doc.name(), header, body);
        for (Object j : doc.inherits()) {
            DocumentmanagerConfig.Datatype.Documenttype.Inherits parent =
                    (DocumentmanagerConfig.Datatype.Documenttype.Inherits) j;
            DataTypeName name = new DataTypeName(parent.name());
            DocumentType parentType = manager.getDocumentType(name);
            if (parentType == null) {
                throw new IllegalArgumentException("Could not find document type '" + name.toString() + "'.");
            }
            type.inherit(parentType);
        }
        Map<String, Collection<String>> fieldSets = new HashMap<>(doc.fieldsets().size());
        for (Map.Entry<String, DocumentmanagerConfig.Datatype.Documenttype.Fieldsets> entry: doc.fieldsets().entrySet()) {
            fieldSets.put(entry.getKey(), entry.getValue().fields());
        }
        type.addFieldSets(fieldSets);
        manager.register(type);
    }

    private static void registerStructType(DocumentmanagerConfig config, DocumentTypeManager manager, int id,
                                           DocumentmanagerConfig.Datatype.Structtype struct) {
        StructDataType type = new StructDataType(id, struct.name());

        if (config.enablecompression()) {
            CompressionConfig comp = makeCompressionConfig(struct);
            type.setCompressionConfig(comp);
        }

        for (Object j : struct.field()) {
            DocumentmanagerConfig.Datatype.Structtype.Field field =
                    (DocumentmanagerConfig.Datatype.Structtype.Field) j;
            DataType fieldType = (field.datatype() == id)
                               ? manager.getDataTypeAndReturnTemporary(field.datatype(), field.detailedtype())
                               : manager.getDataType(field.datatype(), field.detailedtype());

            if (field.id().size() == 1) {
                type.addField(new Field(field.name(), field.id().get(0).id(), fieldType));
            } else {
                type.addField(new Field(field.name(), fieldType));
            }
        }
        manager.register(type);
    }

    private static void registerReferenceType(DocumentTypeManager manager, int id,
                                              DocumentmanagerConfig.Datatype.Referencetype refType) {
        ReferenceDataType referenceType;
        if (manager.hasDataType(refType.target_type_id())) {
            DocumentType targetDocType = (DocumentType)manager.getDataType(refType.target_type_id());
            referenceType = new ReferenceDataType(targetDocType, id);
        } else {
            TemporaryStructuredDataType temporaryTargetType = TemporaryStructuredDataType.createById(refType.target_type_id());
            referenceType = new ReferenceDataType(temporaryTargetType, id);
        }
        // Note: can't combine the above new-statements, as they call different constructors.
        manager.register(referenceType);
    }

    public static DocumentTypeManager configureNewManager(DocumentmanagerConfig config) {
        DocumentTypeManager manager = new DocumentTypeManager();
        if (config == null) {
            return manager;
        }
        configureNewManager(config, manager);
        return manager;
    }

    /**
     * Called by the configuration system to register document types based on documentmanager.cfg.
     *
     * @param config the instance representing config in documentmanager.cfg.
     */
    @Override
    public void configure(DocumentmanagerConfig config) {
        DocumentTypeManager manager = configureNewManager(config);
        int defaultTypeCount = new DocumentTypeManager().getDataTypes().size();
        if (this.managerToConfigure.getDataTypes().size() != defaultTypeCount) {
            log.log(LogLevel.DEBUG, "Live document config overwritten with new config.");
        }
        managerToConfigure.assign(manager);
    }

    private static void setupAnnotationRefTypes(DocumentmanagerConfig config, DocumentTypeManager manager) {
        for (int i = 0; i < config.datatype().size(); i++) {
            DocumentmanagerConfig.Datatype thisDataType = config.datatype(i);
            int id = thisDataType.id();
            for (Object o : thisDataType.annotationreftype()) {
                DocumentmanagerConfig.Datatype.Annotationreftype annRefType = (DocumentmanagerConfig.Datatype.Annotationreftype) o;
                AnnotationType annotationType = manager.getAnnotationTypeRegistry().getType(annRefType.annotation());
                if (annotationType == null) {
                    throw new IllegalArgumentException("Found reference to " + annRefType.annotation() + ", which does not exist!");
                }
                AnnotationReferenceDataType type = new AnnotationReferenceDataType(annotationType, id);
                manager.register(type);
            }
        }
    }

    private static void setupAnnotationTypesWithoutPayloads(DocumentmanagerConfig config, DocumentTypeManager manager) {
        for (DocumentmanagerConfig.Annotationtype annType : config.annotationtype()) {
            AnnotationType annotationType = new AnnotationType(annType.name(), annType.id());
            manager.getAnnotationTypeRegistry().register(annotationType);
        }
    }

    private static void addAnnotationTypePayloads(DocumentmanagerConfig config, DocumentTypeManager manager) {
        for (DocumentmanagerConfig.Annotationtype annType : config.annotationtype()) {
            AnnotationType annotationType = manager.getAnnotationTypeRegistry().getType(annType.id());
            DataType payload = manager.getDataType(annType.datatype(), "");
            if (!payload.equals(DataType.NONE)) {
                annotationType.setDataType(payload);
            }
        }

    }

    private static void addAnnotationTypeInheritance(DocumentmanagerConfig config, DocumentTypeManager manager) {
        for (DocumentmanagerConfig.Annotationtype annType : config.annotationtype()) {
            if (annType.inherits().size() > 0) {
                AnnotationType inheritedType = manager.getAnnotationTypeRegistry().getType(annType.inherits(0).id());
                AnnotationType type = manager.getAnnotationTypeRegistry().getType(annType.id());
                type.inherit(inheritedType);
            }
        }
    }

    private static void addStructInheritance(DocumentmanagerConfig config, DocumentTypeManager manager) {
        for (int i = 0; i < config.datatype().size(); i++) {
            DocumentmanagerConfig.Datatype thisDataType = config.datatype(i);
            int id = thisDataType.id();
            for (Object o : thisDataType.structtype()) {
                DocumentmanagerConfig.Datatype.Structtype struct = (DocumentmanagerConfig.Datatype.Structtype) o;
                StructDataType thisStruct = (StructDataType) manager.getDataType(id, "");

                for (DocumentmanagerConfig.Datatype.Structtype.Inherits parent : struct.inherits()) {
                    StructDataType parentStruct = (StructDataType) manager.getDataType(parent.name());
                    thisStruct.inherit(parentStruct);
                }
            }
        }
    }
}
