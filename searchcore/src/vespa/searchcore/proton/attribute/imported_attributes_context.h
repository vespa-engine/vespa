// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/searchcommon/attribute/i_document_meta_store_context.h>
#include <vespa/vespalib/stllike/hash_fun.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <mutex>
#include <unordered_map>

namespace search { class AttributeGuard; }
namespace search::attribute {
    class AttributeReadGuard;
    class IAttributeVector;
    class ImportedAttributeVector;
}

namespace proton {

class ImportedAttributesRepo;

/**
 * Short lived context class that gives access to all imported attributes in a given repo.
 *
 * Attribute guards and enum guards are cached in this class and released upon destruction.
 */
class ImportedAttributesContext : public search::attribute::IAttributeContext {
private:
    using AttributeGuard = search::AttributeGuard;
    using AttributeReadGuard = search::attribute::AttributeReadGuard;
    using IAttributeVector = search::attribute::IAttributeVector;
    using ImportedAttributeVector = search::attribute::ImportedAttributeVector;
    using IAttributeFunctor = search::attribute::IAttributeFunctor;
    using MetaStoreReadGuard = search::IDocumentMetaStoreContext::IReadGuard;

    using AttributeCache = vespalib::hash_map<vespalib::string, std::unique_ptr<AttributeReadGuard>>;
    using MetaStoreCache = std::unordered_map<const void *, std::shared_ptr<MetaStoreReadGuard>>;
    using LockGuard = std::lock_guard<std::mutex>;

    const ImportedAttributesRepo &_repo;
    bool                          _mtSafe;
    mutable AttributeCache        _guardedAttributes;
    mutable AttributeCache        _enumGuardedAttributes;
    mutable MetaStoreCache        _metaStores;
    mutable std::mutex            _cacheMutex;

    const IAttributeVector *getOrCacheAttribute(const vespalib::string &name, AttributeCache &attributes, bool stableEnumGuard) const;
    const IAttributeVector *getOrCacheAttributeMtSafe(const vespalib::string &name, AttributeCache &attributes, bool stableEnumGuard) const;

public:
    ImportedAttributesContext(const ImportedAttributesRepo &repo);
    ~ImportedAttributesContext() override;

    // Implements search::attribute::IAttributeContext
    const IAttributeVector *getAttribute(const vespalib::string &name) const override;
    const IAttributeVector *getAttributeStableEnum(const vespalib::string &name) const override;
    void getAttributeList(std::vector<const IAttributeVector *> &list) const override;
    void releaseEnumGuards() override;
    void enableMultiThreadSafe() override { _mtSafe = true; }

    void asyncForAttribute(const vespalib::string &name, std::unique_ptr<IAttributeFunctor> func) const override;
};

}
