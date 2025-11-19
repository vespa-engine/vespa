// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
@SuppressWarnings({"deprecation", "removal"})
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
        new ApplyNewDoctypeConfig(config, manager);
    }

    private static class ApplyNewDoctypeConfig {


        public ApplyNewDoctypeConfig(DocumentmanagerConfig config, DocumentTypeManager manager) {
            this.manager = manager;
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
                        addNewType(typeconf.idx(), new GeoPosType(8));
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
