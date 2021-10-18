// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "i_document_db_reference_resolver.h"
#include <vespa/vespalib/stllike/string.h>
#include <vespa/searchlib/attribute/not_implemented_attribute.h>
#include <map>

namespace document {
    class DocumentType;
    class DocumentTypeRepo;
}
namespace search {
    class IAttributeManager;
    struct IDocumentMetaStoreContext;
}
namespace search::attribute {
    class IAttributeVector;
    class ReferenceAttribute;
}
namespace vespa::config::search::internal { class InternalImportedFieldsType; }

namespace vespalib {

class ISequencedTaskExecutor;
class MonitoredRefCount;
}

namespace proton {

class IDocumentDBReference;
class IDocumentDBReferenceRegistry;
class ImportedAttributesRepo;
class GidToLidChangeRegistrator;

/**
 * Class that for a given document db resolves all references to parent document dbs:
 *   1) Connects all reference attributes to gid mappers of parent document dbs.
 */
class DocumentDBReferenceResolver : public IDocumentDBReferenceResolver {
private:
    using ImportedFieldsConfig = const vespa::config::search::internal::InternalImportedFieldsType;
    const IDocumentDBReferenceRegistry &_registry;
    const document::DocumentType       &_thisDocType;
    const ImportedFieldsConfig         &_importedFieldsCfg;
    const document::DocumentType       &_prevThisDocType;
    vespalib::MonitoredRefCount        &_refCount;
    vespalib::ISequencedTaskExecutor     &_attributeFieldWriter;
    bool                                _useReferences;
    std::map<vespalib::string, std::unique_ptr<GidToLidChangeRegistrator>> _registrators;

    GidToLidChangeRegistrator &getRegistrator(const vespalib::string &docTypeName);
    std::shared_ptr<IDocumentDBReference> getTargetDocumentDB(const vespalib::string &refAttrName) const;
    void connectReferenceAttributesToGidMapper(const search::IAttributeManager &attrMgr);
    std::unique_ptr<ImportedAttributesRepo> createImportedAttributesRepo(const search::IAttributeManager &attrMgr,
                                                                         const std::shared_ptr<search::IDocumentMetaStoreContext> &documentMetaStore,
                                                                         bool useSearchCache);
    void detectOldListeners(const search::IAttributeManager &attrMgr);
    void listenToGidToLidChanges(const search::IAttributeManager &attrMgr);

public:
    DocumentDBReferenceResolver(const IDocumentDBReferenceRegistry &registry,
                                const document::DocumentType &thisDocType,
                                const ImportedFieldsConfig &importedFieldsCfg,
                                const document::DocumentType &prevThisDocType,
                                vespalib::MonitoredRefCount &refCount,
                                vespalib::ISequencedTaskExecutor &attributeFieldWriter,
                                bool useReferences);
    ~DocumentDBReferenceResolver() override;

    std::unique_ptr<ImportedAttributesRepo> resolve(const search::IAttributeManager &newAttrMgr,
                                                    const search::IAttributeManager &oldAttrMgr,
                                                    const std::shared_ptr<search::IDocumentMetaStoreContext> &documentMetaStore,
                                                    vespalib::duration visibilityDelay) override;
    void teardown(const search::IAttributeManager &oldAttrMgr) override;
};

}
