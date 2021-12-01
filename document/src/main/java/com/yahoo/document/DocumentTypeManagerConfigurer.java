// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.compress.CompressionType;
import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.document.annotation.AnnotationReferenceDataType;
import com.yahoo.document.annotation.AnnotationType;
import java.util.logging.Level;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Configures the Vespa document manager from a config id.
 *
 * @author Einar M R Rosenvinge
 * @deprecated Will become non-public on Vespa 8
 */
@Deprecated
public class DocumentTypeManagerConfigurer implements ConfigSubscriber.SingleSubscriber<DocumentmanagerConfig>{

    private final static Logger log = Logger.getLogger(DocumentTypeManagerConfigurer.class.getName());

    private final DocumentTypeManager managerToConfigure;

    public DocumentTypeManagerConfigurer(DocumentTypeManager manager) {
        this.managerToConfigure = manager;
    }

    /** Deprecated and will go away on Vespa 8 */
    @Deprecated
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

    /** One-shot configuration; should be called on a newly constructed manager */
    static void configureNewManager(DocumentmanagerConfig config, DocumentTypeManager manager) {
        if (config == null) {
            return;
        }
        new Apply(config, manager);
    }

    private static class Apply {
        public Apply(DocumentmanagerConfig config, DocumentTypeManager manager) {
            this.manager = manager;
            this.usev8geopositions = (config == null) ? false : config.usev8geopositions();
            if (config != null) {
                apply(config);
            }
        }

        private final Map<Integer, DataType> typesById = new HashMap<>();
        private final Map<String, DataType> typesByName = new HashMap<>();
        private final Map<Integer, DocumentmanagerConfig.Datatype> configMap = new HashMap<>();

        private void inProgress(DataType type) {
            var old = typesById.put(type.getId(), type);
            if (old != null) {
                throw new IllegalArgumentException("Multiple types with same id: "+old+" -> "+type);
            }
            old = typesByName.put(type.getName(), type);
            if (old != null) {
                log.warning("Multiple types with same name: "+old+" -> "+type);
            }
        }

        private void startStructsAndDocs(DocumentmanagerConfig config) {
            for (var thisDataType : config.datatype()) {
                for (var o : thisDataType.structtype()) {
                    int id = thisDataType.id();
                    StructDataType type = new StructDataType(id, o.name());
                    inProgress(type);
                    configMap.remove(id);
                }
            }
            for (var thisDataType : config.datatype()) {
                for (var doc : thisDataType.documenttype()) {
                    int id = thisDataType.id();
                    StructDataType header = (StructDataType) typesById.get(doc.headerstruct());
                    var importedFields = doc.importedfield().stream()
                        .map(f -> f.name())
                        .collect(Collectors.toUnmodifiableSet());
                    DocumentType type = new DocumentType(doc.name(), header, importedFields);
                    if (id != type.getId()) {
                        // really old stuff, should rewrite tests using this:
                        int alt = (doc.name()+"."+doc.version()).hashCode();
                        if (id == alt) {
                            typesById.put(id, type);
                        } else {
                            throw new IllegalArgumentException("Document type "+doc.name()+
                                                               " wanted id "+id+" but got "+
                                                               type.getId()+", alternative id was: "+alt);
                        }
                    }
                    inProgress(type);
                    configMap.remove(id);
                }
            }
        }

        private DataType createArrayType(int id, DocumentmanagerConfig.Datatype.Arraytype array) {
            DataType nestedType = getOrCreateType(array.datatype());
            ArrayDataType type = new ArrayDataType(nestedType, id);
            inProgress(type);
            return type;
        }

        private DataType createMapType(int id, DocumentmanagerConfig.Datatype.Maptype map) {
            DataType keyType = getOrCreateType(map.keytype());
            DataType valType = getOrCreateType(map.valtype());
            MapDataType type = new MapDataType(keyType, valType, id);
            inProgress(type);
            return type;
        }

        private DataType createWeightedSetType(int id, DocumentmanagerConfig.Datatype.Weightedsettype wset) {
            DataType nestedType = getOrCreateType(wset.datatype());
            WeightedSetDataType type =
                new WeightedSetDataType(nestedType, wset.createifnonexistant(), wset.removeifzero(), id);
            inProgress(type);
            return type;
        }

        private DataType createReferenceType(int id, DocumentmanagerConfig.Datatype.Referencetype refType) {
            int targetId = refType.target_type_id();
            DocumentType targetDocType = (DocumentType) typesById.get(targetId);
            var type = new ReferenceDataType(targetDocType, id);
            inProgress(type);
            return type;
        }


        private DataType getOrCreateType(int id) {
            if (typesById.containsKey(id)) {
                return typesById.get(id);
            }
            var config = configMap.remove(id);
            if (config == null) {
                return manager.getDataType(id);
            }
            assert(id == config.id());
            for (var o : config.arraytype()) {
                return createArrayType(id, o);
            }
            for (var o : config.maptype()) {
                return createMapType(id, o);
            }
            for (var o : config.weightedsettype()) {
                return createWeightedSetType(id, o);
            }
            for (var o : config.referencetype()) {
                return createReferenceType(id, o);
            }
            throw new IllegalArgumentException("Could not create type from config: "+config);
        }

        private void createRemainingTypes(DocumentmanagerConfig config) {
            for (var thisDataType : config.datatype()) {
                int id = thisDataType.id();
                var type = getOrCreateType(id);
                assert(type != null);
            }
        }

        private void fillStructs(DocumentmanagerConfig config) {
            for (var thisDataType : config.datatype()) {
                for (var struct : thisDataType.structtype()) {
                    int id = thisDataType.id();
                    StructDataType type = (StructDataType) typesById.get(id);
                    for (var parent : struct.inherits()) {
                        var parentStruct = (StructDataType) typesByName.get(parent.name());
                        type.inherit(parentStruct);
                    }
                    for (var field : struct.field()) {
                        if (field.datatype() == id) {
                            log.fine("Self-referencing struct "+struct.name()+" field: "+field);
                        }
                        DataType fieldType = typesById.get(field.datatype());
                        if (fieldType == null) {
                            fieldType = manager.getDataType(field.datatype(), field.detailedtype());
                        }
                        if (field.id().size() == 1) {
                            type.addField(new Field(field.name(), field.id().get(0).id(), fieldType));
                        } else {
                            type.addField(new Field(field.name(), fieldType));
                        }
                    }
                }
            }
        }

        private void fillDocuments(DocumentmanagerConfig config) {
            for (var thisDataType : config.datatype()) {
                for (var doc : thisDataType.documenttype()) {
                    int id = thisDataType.id();
                    DocumentType type = (DocumentType) typesById.get(id);
                    for (var parent : doc.inherits()) {
                        DocumentType parentType = (DocumentType) typesByName.get(parent.name());
                        if (parentType == null) {
                            DataTypeName name = new DataTypeName(parent.name());
                            parentType = manager.getDocumentType(name);
                        }
                        if (parentType == null) {
                            throw new IllegalArgumentException("Could not find parent document type '" + parent.name() + "'.");
                        }
                        type.inherit(parentType);
                    }
                    Map<String, Collection<String>> fieldSets = new HashMap<>(doc.fieldsets().size());
                    for (Map.Entry<String, DocumentmanagerConfig.Datatype.Documenttype.Fieldsets> entry: doc.fieldsets().entrySet()) {
                        fieldSets.put(entry.getKey(), entry.getValue().fields());
                    }
                    type.addFieldSets(fieldSets);
                }
            }
        }

        private void splitConfig(DocumentmanagerConfig config) {
            for (var dataTypeConfig : config.datatype()) {
                int id = dataTypeConfig.id();
                var old = configMap.put(id, dataTypeConfig);
                if (old != null) {
                    throw new IllegalArgumentException
                        ("Multiple configs for id "+id+" first: "+old+" second: "+dataTypeConfig);
                }
            }
        }

        private void apply(DocumentmanagerConfig config) {
            splitConfig(config);
            setupAnnotationTypesWithoutPayloads(config);
            setupAnnotationRefTypes(config);
            startStructsAndDocs(config);
            createRemainingTypes(config);
            fillStructs(config);
            fillDocuments(config);
            for (DataType type : typesById.values()) {
                manager.register(type);
            }
            addAnnotationTypePayloads(config);
            addAnnotationTypeInheritance(config);
        }

        private void setupAnnotationRefTypes(DocumentmanagerConfig config) {
            for (var thisDataType : config.datatype()) {
                int id = thisDataType.id();
                for (var annRefType : thisDataType.annotationreftype()) {
                    AnnotationType annotationType = manager.getAnnotationTypeRegistry().getType(annRefType.annotation());
                    if (annotationType == null) {
                        throw new IllegalArgumentException("Found reference to " + annRefType.annotation() + ", which does not exist!");
                    }
                    AnnotationReferenceDataType type = new AnnotationReferenceDataType(annotationType, id);
                    inProgress(type);
                    configMap.remove(id);
                }
            }
        }

        private void setupAnnotationTypesWithoutPayloads(DocumentmanagerConfig config) {
            for (DocumentmanagerConfig.Annotationtype annType : config.annotationtype()) {
                AnnotationType annotationType = new AnnotationType(annType.name(), annType.id());
                manager.getAnnotationTypeRegistry().register(annotationType);
            }
        }

        private void addAnnotationTypePayloads(DocumentmanagerConfig config) {
            for (DocumentmanagerConfig.Annotationtype annType : config.annotationtype()) {
                AnnotationType annotationType = manager.getAnnotationTypeRegistry().getType(annType.id());
                DataType payload = manager.getDataType(annType.datatype(), "");
                if (! payload.equals(DataType.NONE)) {
                    annotationType.setDataType(payload);
                }
            }

        }

        private void addAnnotationTypeInheritance(DocumentmanagerConfig config) {
            for (DocumentmanagerConfig.Annotationtype annType : config.annotationtype()) {
                if (annType.inherits().size() > 0) {
                    AnnotationType inheritedType = manager.getAnnotationTypeRegistry().getType(annType.inherits(0).id());
                    AnnotationType type = manager.getAnnotationTypeRegistry().getType(annType.id());
                    type.inherit(inheritedType);
                }
            }
        }

        private final boolean usev8geopositions;
        private final DocumentTypeManager manager;
    }

    public static DocumentTypeManager configureNewManager(DocumentmanagerConfig config) {
        DocumentTypeManager manager = new DocumentTypeManager();
        new Apply(config, manager);
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
            log.log(Level.FINE, "Live document config overwritten with new config.");
        }
        managerToConfigure.assign(manager);
    }

}
