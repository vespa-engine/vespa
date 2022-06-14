// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "readable_attribute_vector.h"
#include <vespa/searchlib/common/i_document_meta_store_context.h>
#include <vespa/vespalib/stllike/string.h>

namespace search { struct IDocumentMetaStoreContext; }

namespace search::attribute {

class BitVectorSearchCache;
class ReadableAttributeVector;
class ReferenceAttribute;

/**
 * Attribute vector which does not store values of its own, but rather serves as a
 * convenient indirection wrapper towards a target vector, usually in another
 * document type altogether. Imported attributes are meant to be used in conjunction
 * with a reference attribute, which specifies a dynamic mapping from a local LID to
 * a target LID (via an intermediate GID).
 *
 * Any accessor on the imported attribute for a local LID yields the same result as
 * if the same accessor were invoked with the target LID on the target attribute vector.
 */
class ImportedAttributeVector : public ReadableAttributeVector {
public:
    using SP = std::shared_ptr<ImportedAttributeVector>;
    using MetaStoreReadGuard = search::IDocumentMetaStoreContext::IReadGuard;
    ImportedAttributeVector(vespalib::stringref name,
                            std::shared_ptr<ReferenceAttribute> reference_attribute,
                            std::shared_ptr<IDocumentMetaStoreContext> document_meta_store,
                            std::shared_ptr<ReadableAttributeVector> target_attribute,
                            std::shared_ptr<const IDocumentMetaStoreContext> target_document_meta_store,
                            bool use_search_cache);
    ImportedAttributeVector(vespalib::stringref name,
                            std::shared_ptr<ReferenceAttribute> reference_attribute,
                            std::shared_ptr<IDocumentMetaStoreContext> document_meta_store,
                            std::shared_ptr<ReadableAttributeVector> target_attribute,
                            std::shared_ptr<const IDocumentMetaStoreContext> target_document_meta_store,
                            std::shared_ptr<BitVectorSearchCache> search_cache);
    ~ImportedAttributeVector() override;

    const std::shared_ptr<ReferenceAttribute>& getReferenceAttribute() const noexcept {
        return _reference_attribute;
    }
    const std::shared_ptr<IDocumentMetaStoreContext> &getDocumentMetaStore() const {
        return _document_meta_store;
    }
    const std::shared_ptr<ReadableAttributeVector>& getTargetAttribute() const noexcept {
        return _target_attribute;
    }
    const std::shared_ptr<const IDocumentMetaStoreContext> &getTargetDocumentMetaStore() const {
        return _target_document_meta_store;
    }
    const std::shared_ptr<BitVectorSearchCache> &getSearchCache() const {
        return _search_cache;
    }
    void clearSearchCache();
    const vespalib::string &getName() const {
        return _name;
    }

    std::unique_ptr<AttributeReadGuard> makeReadGuard(bool stableEnumGuard) const override;
    virtual std::unique_ptr<AttributeReadGuard> makeReadGuard(std::shared_ptr<MetaStoreReadGuard> targetMetaStoreReadGuard, bool stableEnumGuard) const;

protected:
    vespalib::string                           _name;
    std::shared_ptr<ReferenceAttribute>        _reference_attribute;
    std::shared_ptr<IDocumentMetaStoreContext> _document_meta_store;
    std::shared_ptr<ReadableAttributeVector>   _target_attribute;
    std::shared_ptr<const IDocumentMetaStoreContext> _target_document_meta_store;
    std::shared_ptr<BitVectorSearchCache>      _search_cache;
};

}
