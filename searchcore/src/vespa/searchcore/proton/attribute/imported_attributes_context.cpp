// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "imported_attributes_context.h"
#include "imported_attributes_repo.h"
#include <vespa/searchlib/attribute/attribute_read_guard.h>
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/attribute/imported_attribute_vector.h>
#include <vespa/vespalib/stllike/hash_map.hpp>

using search::attribute::IAttributeVector;
using search::attribute::ImportedAttributeVector;
using LockGuard = std::lock_guard<std::mutex>;

namespace proton {

const IAttributeVector *
ImportedAttributesContext::getOrCacheAttribute(const vespalib::string &name, AttributeCache &attributes, bool stableEnumGuard) const
{
    auto itr = attributes.find(name);
    if (itr != attributes.end()) {
        return itr->second->attribute();
    }
    const ImportedAttributeVector::SP & result = _repo.get(name);
    if (result) {
        auto metaItr = _metaStores.find(result->getTargetDocumentMetaStore().get());
        std::shared_ptr<MetaStoreReadGuard> metaGuard;
        if (metaItr == _metaStores.end()) {
            metaGuard = result->getTargetDocumentMetaStore()->getReadGuard();
            _metaStores.emplace(result->getTargetDocumentMetaStore().get(), metaGuard);
        } else {
            metaGuard = metaItr->second;
        }
        auto insRes = attributes.insert(std::make_pair(name, result->makeReadGuard(std::move(metaGuard), stableEnumGuard)));
        return insRes.first->second->attribute();
    } else {
        return nullptr;
    }
}

const IAttributeVector *
ImportedAttributesContext::getOrCacheAttributeMtSafe(const vespalib::string &name, AttributeCache &attributes, bool stableEnumGuard) const {
    LockGuard guard(_cacheMutex);
    return getOrCacheAttribute(name, attributes, stableEnumGuard);
}

ImportedAttributesContext::ImportedAttributesContext(const ImportedAttributesRepo &repo)
    : _repo(repo),
      _mtSafe(false),
      _guardedAttributes(),
      _enumGuardedAttributes(),
      _metaStores(),
      _cacheMutex()
{
}

ImportedAttributesContext::~ImportedAttributesContext() = default;

const IAttributeVector *
ImportedAttributesContext::getAttribute(const vespalib::string &name) const
{
    return _mtSafe
        ? getOrCacheAttributeMtSafe(name, _guardedAttributes, false)
        : getOrCacheAttribute(name, _guardedAttributes, false);
}

const IAttributeVector *
ImportedAttributesContext::getAttributeStableEnum(const vespalib::string &name) const
{
    return _mtSafe
           ? getOrCacheAttributeMtSafe(name, _enumGuardedAttributes, true)
           : getOrCacheAttribute(name, _enumGuardedAttributes, true);
}

void
ImportedAttributesContext::getAttributeList(std::vector<const IAttributeVector *> &list) const
{
    std::vector<ImportedAttributeVector::SP> attributes;
    _repo.getAll(attributes);
    for (const auto &attr : attributes) {
        list.push_back(getAttribute(attr->getName()));
    }
}

void
ImportedAttributesContext::releaseEnumGuards()
{
    if (_mtSafe) {
        LockGuard guard(_cacheMutex);
        _enumGuardedAttributes.clear();
    } else {
        _enumGuardedAttributes.clear();
    }
}

void
ImportedAttributesContext::asyncForAttribute(const vespalib::string &, std::unique_ptr<IAttributeFunctor> ) const {
    throw std::runtime_error("proton::ImportedAttributesContext::asyncForAttribute should never be called.");
}

}
