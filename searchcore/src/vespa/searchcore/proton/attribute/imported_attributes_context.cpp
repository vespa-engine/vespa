// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include "imported_attributes_context.h"
#include "imported_attributes_repo.h"
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/attribute/imported_attribute_vector.h>
#include <vespa/vespalib/stllike/hash_map.hpp>

using search::attribute::IAttributeVector;
using search::attribute::ImportedAttributeVector;
using LockGuard = std::lock_guard<std::mutex>;

namespace proton {

ImportedAttributesContext::GuardedAttribute::GuardedAttribute(ImportedAttributeVector::SP attr,
                                                              bool stableEnumGuard)
    : _attr(std::move(attr)),
      _guard(stableEnumGuard ? _attr->acquireEnumGuard() : _attr->acquireGuard())
{
}

ImportedAttributesContext::GuardedAttribute::~GuardedAttribute()
{
}

const IAttributeVector *
ImportedAttributesContext::GuardedAttribute::get() const
{
    return _attr.get();
}

const IAttributeVector *
ImportedAttributesContext::getOrCacheAttribute(const vespalib::string &name,
                                               AttributeCache &attributes,
                                               bool stableEnumGuard,
                                               const LockGuard &) const
{
    auto itr = attributes.find(name);
    if (itr != attributes.end()) {
        return itr->second.get();
    }
    ImportedAttributeVector::SP result = _repo.get(name);
    if (result.get() != nullptr) {
        attributes.emplace(name, GuardedAttribute(result, stableEnumGuard));
        return result.get();
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

ImportedAttributesContext::~ImportedAttributesContext()
{
}

const IAttributeVector *
ImportedAttributesContext::getAttribute(const vespalib::string &name) const
{
    LockGuard guard(_cacheMutex);
    return getOrCacheAttribute(name, _guardedAttributes, false, guard);
}

const IAttributeVector *
ImportedAttributesContext::getAttributeStableEnum(const vespalib::string &name) const
{
    LockGuard guard(_cacheMutex);
    return getOrCacheAttribute(name, _enumGuardedAttributes, true, guard);
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
    LockGuard guard(_cacheMutex);
    _enumGuardedAttributes.clear();
}
    
}
