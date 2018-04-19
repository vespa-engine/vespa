// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/vespalib/stllike/hash_fun.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <mutex>
#include <unordered_map>

namespace search {
class AttributeGuard;
namespace attribute {
class AttributeReadGuard;
class IAttributeVector;
class ImportedAttributeVector;
}}

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

    using AttributeCache = std::unordered_map<vespalib::string, std::unique_ptr<AttributeReadGuard>, vespalib::hash<vespalib::string>>;
    using LockGuard = std::lock_guard<std::mutex>;

    const ImportedAttributesRepo &_repo;
    mutable AttributeCache _guardedAttributes;
    mutable AttributeCache _enumGuardedAttributes;
    mutable std::mutex _cacheMutex;

    const IAttributeVector *getOrCacheAttribute(const vespalib::string &name,
                                                AttributeCache &attributes,
                                                bool stableEnumGuard,
                                                const LockGuard &) const;

public:
    ImportedAttributesContext(const ImportedAttributesRepo &repo);
    ~ImportedAttributesContext();

    // Implements search::attribute::IAttributeContext
    virtual const IAttributeVector *getAttribute(const vespalib::string &name) const override;
    virtual const IAttributeVector *getAttributeStableEnum(const vespalib::string &name) const override;
    virtual void getAttributeList(std::vector<const IAttributeVector *> &list) const override;
    virtual void releaseEnumGuards() override;
};

}
