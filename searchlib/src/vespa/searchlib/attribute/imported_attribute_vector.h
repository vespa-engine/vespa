// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "reference_attribute.h"
#include <vespa/vespalib/stllike/string.h>
#include <memory>

namespace search {

class AttributeGuard;
class AttributeEnumGuard;
class IDocumentMetaStoreContext;

namespace attribute {

class BitVectorSearchCache;

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
class ImportedAttributeVector {
public:
    using SP = std::shared_ptr<ImportedAttributeVector>;
    ImportedAttributeVector(vespalib::stringref name,
                            std::shared_ptr<ReferenceAttribute> reference_attribute,
                            std::shared_ptr<AttributeVector> target_attribute,
                            std::shared_ptr<IDocumentMetaStoreContext> document_meta_store,
                            bool use_search_cache);
    ImportedAttributeVector(vespalib::stringref name,
                            std::shared_ptr<ReferenceAttribute> reference_attribute,
                            std::shared_ptr<AttributeVector> target_attribute,
                            std::shared_ptr<IDocumentMetaStoreContext> document_meta_store,
                            std::shared_ptr<BitVectorSearchCache> search_cache);
    virtual ~ImportedAttributeVector();

    const std::shared_ptr<ReferenceAttribute>& getReferenceAttribute() const noexcept {
        return _reference_attribute;
    }
    const std::shared_ptr<AttributeVector>& getTargetAttribute() const noexcept {
        return _target_attribute;
    }
    const std::shared_ptr<IDocumentMetaStoreContext> &getDocumentMetaStore() const {
        return _document_meta_store;
    }
    const std::shared_ptr<BitVectorSearchCache> &getSearchCache() const {
        return _search_cache;
    }
    void clearSearchCache();
    const vespalib::string &getName() const {
        return _name;
    }

    /*
     * Create an imported attribute with a snapshot of lid to lid mapping.
     */
    virtual std::unique_ptr<IAttributeVector> makeReadGuard(bool stableEnumGuard) const;

protected:
    vespalib::string                           _name;
    std::shared_ptr<ReferenceAttribute>        _reference_attribute;
    std::shared_ptr<AttributeVector>           _target_attribute;
    std::shared_ptr<IDocumentMetaStoreContext> _document_meta_store;
    std::shared_ptr<BitVectorSearchCache>      _search_cache;
};

}
}
