// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.document.annotation.AnnotationReferenceDataType;
import com.yahoo.document.annotation.AnnotationType;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.document.internal.GeoPosType;
import com.yahoo.tensor.TensorType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Configures the Vespa document manager from a config id.
 *
 * @author Einar M R Rosenvinge
 */
public class DocumentTypeManagerConfigurer implements ConfigSubscriber.SingleSubscriber<DocumentmanagerConfig> {

    private final static Logger log = Logger.getLogger(DocumentTypeManagerConfigurer.class.getName());

    private final DocumentTypeManager managerToConfigure;

    public DocumentTypeManagerConfigurer(DocumentTypeManager manager) {
        this.managerToConfigure = manager;
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
        try {
            subscriber.subscribe(this, DocumentmanagerConfig.class, configId);
            return subscriber;
        } catch (RuntimeException e) {
            subscriber.close();
            throw e;
        }
    }

    /** One-shot configuration; should be called on a newly constructed manager */
    static void configureNewManager(DocumentmanagerConfig config, DocumentTypeManager manager) {
        if (config == null) return;
        manager.setIgnoreUndefinedFields(config.ignoreundefinedfields());
        new Apply(config, manager);
        if (config.datatype().size() == 0 && config.annotationtype().size() == 0) {
            new ApplyNewDoctypeConfig(config, manager);
        }
    }

    private static class Apply {

        public Apply(DocumentmanagerConfig config, DocumentTypeManager manager) {
            this.manager = manager;
            this.usev8geopositions = config.usev8geopositions();
            apply(config);
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

        boolean looksLikePosition(StructDataType type) {
            var pos = PositionDataType.INSTANCE;
            return type.getName().equals(pos.getName()) && type.getId() == pos.getId();
        }

        private void startStructsAndDocs(DocumentmanagerConfig config) {
            for (var thisDataType : config.datatype()) {
                for (var o : thisDataType.structtype()) {
                    int id = thisDataType.id();
                    StructDataType type = new StructDataType(id, o.name());
                    if (usev8geopositions && looksLikePosition(type)) {
                        type = new GeoPosType(8);
                    }
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
                        typesById.put(id, type);
                        // really old stuff, should rewrite tests using this:
                        int alt = (doc.name()+"."+doc.version()).hashCode();
                        log.warning("Document type "+doc.name()+
                                    " wanted id "+id+" but got "+
                                    type.getId()+", alternative id was: "+alt);
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
                return manager.getDataTypeByCode(id);
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
                    if (type instanceof GeoPosType) {
                        continue;
                    }
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
                            fieldType = manager.getDataTypeByCode(field.datatype(), field.detailedtype());
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
                        ("Multiple configs for id "+id+" first:\n"+old+"\nsecond:\n"+dataTypeConfig);
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
                DataType payload = manager.getDataTypeByCode(annType.datatype(), "");
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

    private static class ApplyNewDoctypeConfig {


        public ApplyNewDoctypeConfig(DocumentmanagerConfig config, DocumentTypeManager manager) {
            this.manager = manager;
            this.usev8geopositions = config.usev8geopositions();

            apply(config);
        }

        Map<Integer, DataType> typesByIdx = new HashMap<>();

        DataType addNewType(int id, DataType type) {
            if (type == null) {
                throw new IllegalArgumentException("Type to add for idx "+id+" cannot be null");
            }
            var old = typesByIdx.put(id, type);
            if (old != null) {
                throw new IllegalArgumentException("Type "+type+" for idx "+id+" conflict: "+old+" present");
            }
            return type;
        }

        Map<Integer, Supplier<DataType>> factoryByIdx = new HashMap<>();

        ArrayList<Integer> proxyRefs = new ArrayList<>();

        private DataType getOrCreateType(int id) {
            if (typesByIdx.containsKey(id)) {
                return typesByIdx.get(id);
            }
            var factory = factoryByIdx.remove(id);
            if (factory != null) {
                DataType type = factory.get();
                return addNewType(id, type);
            }
            throw new IllegalArgumentException("No type or factory found for idx: "+id);
        }

        void createComplexTypes() {
            var toCreate = new ArrayList<>(factoryByIdx.keySet());
            for (var dataTypeId : toCreate) {
                var type = getOrCreateType(dataTypeId);
                assert(type != null);
            }
        }

        class PerDocTypeData {

            DocumentmanagerConfig.Doctype docTypeConfig;

            DocumentType docType = null;

            PerDocTypeData(DocumentmanagerConfig.Doctype config) {
                this.docTypeConfig = config;
            }

            void createSimpleTypes() {
                for (var typeconf : docTypeConfig.primitivetype()) {
                    DataType type = manager.getDataTypeInternal(typeconf.name());
                    if (! (type instanceof PrimitiveDataType)) {
                        throw new IllegalArgumentException("Needed primitive type for '"+typeconf.name()+"' [idx "+typeconf.idx()+"] but got: "+type);
                    }
                    addNewType(typeconf.idx(), type);
                }
                for (var typeconf : docTypeConfig.tensortype()) {
                    var type = new TensorDataType(TensorType.fromSpec(typeconf.detailedtype()));
                    addNewType(typeconf.idx(), type);
                }
            }

            void createFactories() {
                for (var typeconf : docTypeConfig.arraytype()) {
                    factoryByIdx.put(typeconf.idx(), () -> new ArrayDataType(getOrCreateType(typeconf.elementtype())));
                }
                for (var typeconf : docTypeConfig.maptype()) {
                    factoryByIdx.put(typeconf.idx(), () -> new MapDataType(getOrCreateType(typeconf.keytype()),
                                                                           getOrCreateType(typeconf.valuetype())));
                }
                for (var typeconf : docTypeConfig.wsettype()) {
                    factoryByIdx.put(typeconf.idx(), () -> new WeightedSetDataType(getOrCreateType(typeconf.elementtype()),
                                                                                   typeconf.createifnonexistent(),
                                                                                   typeconf.removeifzero()));
                }
                for (var typeconf : docTypeConfig.documentref()) {
                    factoryByIdx.put(typeconf.idx(), () -> ReferenceDataType.createWithInferredId(inProgressById.get(typeconf.targettype()).docType));
                }
                for (var typeconf : docTypeConfig.annotationref()) {
                    factoryByIdx.put(typeconf.idx(), () -> new AnnotationReferenceDataType
                                     (annTypeFromIdx(typeconf.annotationtype())));
                }
            }

            private final Field POS_X = PositionDataType.INSTANCE.getField(PositionDataType.FIELD_X);
            private final Field POS_Y = PositionDataType.INSTANCE.getField(PositionDataType.FIELD_Y);

            boolean isPositionStruct(DocumentmanagerConfig.Doctype.Structtype cfg) {
                if (! cfg.name().equals(PositionDataType.STRUCT_NAME)) return false;
                if (! cfg.inherits().isEmpty()) return false;
                if (cfg.field().size() != 2) return false;
                var f0 = cfg.field(0);
                var f1 = cfg.field(1);
                if (! f0.name().equals(POS_X.getName())) return false;
                if (! f1.name().equals(POS_Y.getName())) return false;
                if (f0.internalid() != POS_X.getId()) return false;
                if (f1.internalid() != POS_Y.getId()) return false;
                if (typesByIdx.get(f0.type()) != POS_X.getDataType()) return false;
                if (typesByIdx.get(f1.type()) != POS_Y.getDataType()) return false;
                return true;
            }

            void createEmptyStructs() {
                for (var typeconf : docTypeConfig.structtype()) {
                    if (isPositionStruct(typeconf)) {
                        int geoVersion = usev8geopositions ? 8 : 7;
                        addNewType(typeconf.idx(), new GeoPosType(geoVersion));
                    } else {
                        addNewType(typeconf.idx(), new StructDataType(typeconf.name()));
                    }
                }
            }

            void initializeDocType() {
                Set<String> importedFields = new HashSet<>();
                for (var imported : docTypeConfig.importedfield()) {
                    importedFields.add(imported.name());
                }
                int contentIdx = docTypeConfig.contentstruct();
                DataType contentStruct = typesByIdx.get(contentIdx);
                if (! (contentStruct instanceof StructDataType)) {
                    throw new IllegalArgumentException("Content struct for document type "+docTypeConfig.name()+
                                                       " should be a struct, but was: "+contentStruct);
                }
                if (docTypeConfig.name().equals(DataType.DOCUMENT.getName())) {
                    this.docType = DataType.DOCUMENT;
                } else {
                    this.docType = new DocumentType(docTypeConfig.name(), (StructDataType)contentStruct, importedFields);
                }
                addNewType(docTypeConfig.idx(), docType);
            }

            void createEmptyAnnotationTypes() {
                for (var typeconf : docTypeConfig.annotationtype()) {
                    AnnotationType annType = manager.getAnnotationTypeRegistry().getType(typeconf.name());
                    if (typeconf.internalid() != -1) {
                        if (annType == null) {
                            annType = new AnnotationType(typeconf.name(), typeconf.internalid());
                        } else {
                            if (annType.getId() != typeconf.internalid()) {
                                throw new IllegalArgumentException("Wrong internalid for annotation type "+annType+
                                                                   " (wanted "+typeconf.internalid()+", got "+annType.getId()+")");
                            }
                        }
                    } else if (annType == null) {
                        annType = new AnnotationType(typeconf.name());
                    }
                    manager.getAnnotationTypeRegistry().register(annType);
                    // because AnnotationType is not a DataType, make a proxy
                    var proxy = new AnnotationReferenceDataType(annType);
                    proxyRefs.add(typeconf.idx());
                    addNewType(typeconf.idx(), proxy);
                }
            }

            AnnotationType annTypeFromIdx(int idx) {
                var proxy = (AnnotationReferenceDataType) typesByIdx.get(idx);
                if (proxy == null) {
                    throw new IllegalArgumentException("Needed AnnotationType for idx "+idx+", found: "+typesByIdx.get(idx));
                }
                return proxy.getAnnotationType();
            }

            void fillAnnotationTypes() {
                for (var typeConf : docTypeConfig.annotationtype()) {
                    var annType = annTypeFromIdx(typeConf.idx());
                    int pIdx = typeConf.datatype();
                    if (pIdx != -1) {
                        DataType payload = getOrCreateType(pIdx);
                        annType.setDataType(payload);
                    }
                    for (var inherit : typeConf.inherits()) {
                        var inheritedType = annTypeFromIdx(inherit.idx());
                        if (! annType.inherits(inheritedType)) {
                            annType.inherit(inheritedType);
                        }
                    }
                }
            }
            void fillStructs() {
                for (var structCfg : docTypeConfig.structtype()) {
                    if (isPositionStruct(structCfg)) {
                        continue;
                    }
                    int idx = structCfg.idx();
                    StructDataType type = (StructDataType) typesByIdx.get(idx);
                    for (var parent : structCfg.inherits()) {
                        var parentStruct = (StructDataType) typesByIdx.get(parent.type());
                        type.inherit(parentStruct);
                    }
                    for (var fieldCfg : structCfg.field()) {
                        if (fieldCfg.type() == idx) {
                            log.fine("Self-referencing struct "+structCfg.name()+" field: "+fieldCfg);
                        }
                        DataType fieldType = getOrCreateType(fieldCfg.type());
                        type.addField(new Field(fieldCfg.name(), fieldCfg.internalid(), fieldType));
                    }
                    if (docType != DataType.DOCUMENT) {
                        docType.addDeclaredStructType(structCfg.name(), type);
                    }
                }
            }
            void fillDocument() {
                for (var inherit : docTypeConfig.inherits()) {
                    var data = inProgressById.get(inherit.idx());
                    if (data == null) {
                        throw new IllegalArgumentException("Missing doctype for inherit idx: "+inherit.idx());
                    } else {
                        docType.inherit(data.docType);
                    }
                }
                Map<String, Collection<String>> fieldSets = new HashMap<>();
                for (var entry : docTypeConfig.fieldsets().entrySet()) {
                    fieldSets.put(entry.getKey(), entry.getValue().fields());
                }
                Set<String> importedFields = new HashSet<>();
                for (var imported : docTypeConfig.importedfield()) {
                    importedFields.add(imported.name());
                }
                docType.addFieldSets(fieldSets);
            }
        }

        private final Map<String, PerDocTypeData> inProgressByName = new HashMap<>();
        private final Map<Integer, PerDocTypeData> inProgressById = new HashMap<>();

        private void apply(DocumentmanagerConfig config) {
            for (var docType : config.doctype()) {
                int idx = docType.idx();
                String name = docType.name();
                var data = new PerDocTypeData(docType);
                var old = inProgressById.put(idx, data);
                if (old != null) {
                    throw new IllegalArgumentException("Multiple document types with id: "+idx);
                }
                old = inProgressByName.put(name, data);
                if (old != null) {
                    throw new IllegalArgumentException("Multiple document types with name: "+name);
                }
            }
            for (var docType : config.doctype()) {
                var docTypeData = inProgressById.get(docType.idx());
                docTypeData.createSimpleTypes();
                docTypeData.createEmptyStructs();
                docTypeData.initializeDocType();
                docTypeData.createEmptyAnnotationTypes();
                docTypeData.createFactories();
            }
            createComplexTypes();
            for (var docType : config.doctype()) {
                var docTypeData = inProgressById.get(docType.idx());
                docTypeData.fillStructs();
                docTypeData.fillDocument();
                docTypeData.fillAnnotationTypes();
            }
            for (int idx : proxyRefs) {
                typesByIdx.remove(idx);
            }
            for (var docTypeData : inProgressByName.values()) {
                manager.registerSingleType(docTypeData.docType);
            }
        }

        private final boolean usev8geopositions;
        private final DocumentTypeManager manager;
    }

    public static DocumentTypeManager configureNewManager(DocumentmanagerConfig config) {
        DocumentTypeManager manager = new DocumentTypeManager();
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
            log.log(Level.FINE, "Live document config overwritten with new config.");
        }
        managerToConfigure.internalAssign(manager);
    }

}
