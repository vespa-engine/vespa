// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "imported_attributes_context.h"
#include "imported_attributes_repo.h"
#include <vespa/searchlib/attribute/attribute_read_guard.h>
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/attribute/imported_attribute_vector.h>
#include <vespa/vespalib/stllike/hash_map.hpp>

using search::attribute::IAttributeVector;
using search::attribute::ImportedAttributeVector;

namespace proton {

const IAttributeVector *
ImportedAttributesContext::getOrCacheAttribute(const vespalib::string &name, AttributeCache &attributes,
                                               bool stableEnumGuard) const
{
    std::unique_lock guard(_cacheMutex);
    std::shared_future<std::unique_ptr<AttributeReadGuard>> future_read_guard;
    auto itr = attributes.find(name);
    if (itr != attributes.end()) {
        future_read_guard = itr->second;
        guard.unlock();
    } else {
        std::promise<std::unique_ptr<AttributeReadGuard>> promise;
        future_read_guard = promise.get_future().share();
        attributes.emplace(name, future_read_guard);
        guard.unlock();
        ImportedAttributeVector::SP result = _repo.get(name);
        if (result) {
            promise.set_value(result->makeReadGuard(stableEnumGuard));
        } else {
            promise.set_value(std::unique_ptr<AttributeReadGuard>());
        }
    }
    auto& read_guard = future_read_guard.get();
    if (read_guard) {
        return read_guard->attribute();
    } else {
        return nullptr;
    }
}

ImportedAttributesContext::ImportedAttributesContext(const ImportedAttributesRepo &repo)
    : _repo(repo),
      _guardedAttributes(),
      _enumGuardedAttributes(),
      _cacheMutex()
{
}

ImportedAttributesContext::~ImportedAttributesContext() = default;

const IAttributeVector *
ImportedAttributesContext::getAttribute(const vespalib::string &name) const
{
    return getOrCacheAttribute(name, _guardedAttributes, false);
}

const IAttributeVector *
ImportedAttributesContext::getAttributeStableEnum(const vespalib::string &name) const
{
    return getOrCacheAttribute(name, _enumGuardedAttributes, true);
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
    std::lock_guard guard(_cacheMutex);
    _enumGuardedAttributes.clear();
}

void
ImportedAttributesContext::asyncForAttribute(const vespalib::string &, std::unique_ptr<IAttributeFunctor> ) const {
    throw std::runtime_error("proton::ImportedAttributesContext::asyncForAttribute should never be called.");
}

}
