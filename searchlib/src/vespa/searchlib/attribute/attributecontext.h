// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include "iattributemanager.h"
#include <mutex>

namespace search {

/**
 * This class is wrapping an attribute manager and
 * implements the IAttributeContext interface to provide read access to attribute vectors.
 **/
class AttributeContext : public attribute::IAttributeContext
{
private:
    using AttributeMap = vespalib::hash_map<string, std::unique_ptr<attribute::AttributeReadGuard>>;
    using IAttributeVector = attribute::IAttributeVector;
    using IAttributeFunctor = attribute::IAttributeFunctor;

    const IAttributeManager & _manager;
    bool                      _mtSafe;
    mutable AttributeMap      _attributes;
    mutable AttributeMap      _enumAttributes;
    mutable std::mutex        _cacheLock;

    const IAttributeVector *getAttribute(AttributeMap & map, const string & name, bool stableEnum) const;
    const IAttributeVector *getAttributeMtSafe(AttributeMap & map, const string & name, bool stableEnum) const;
public:
    AttributeContext(const IAttributeManager & manager);
    ~AttributeContext() override;

    // Implements IAttributeContext
    const attribute::IAttributeVector * getAttribute(const string & name) const override;
    void asyncForAttribute(const vespalib::string &name, std::unique_ptr<IAttributeFunctor> func) const override;
    const attribute::IAttributeVector * getAttributeStableEnum(const string & name) const override;
    void getAttributeList(std::vector<const IAttributeVector *> & list) const override;
    void releaseEnumGuards() override;
    void enableMultiThreadSafe() override { _mtSafe = true; }

    // Give acces to the underlying manager
    const IAttributeManager & getManager() const { return _manager; }
};

} // namespace search

