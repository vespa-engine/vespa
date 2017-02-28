// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributecontext.h"
#include "attributevector.h"
#include <vespa/vespalib/stllike/hash_map.hpp>

using namespace search;
using namespace search::attribute;

namespace search {

const IAttributeVector *
AttributeContext::getAttribute(AttributeMap & map, const string & name, bool stableEnum) const
{
    AttributeMap::const_iterator itr = map.find(name);
    if (itr != map.end()) {
        return itr->second->get();
    } else {
        AttributeGuard::UP ret;
        if (stableEnum) {
            ret = _manager.getAttributeStableEnum(name);
        } else {
            ret = _manager.getAttribute(name);
        }
        if (ret) {
            const AttributeGuard & guard = *ret;
            map[name] = std::move(ret);
            return guard.get();
        }
        return nullptr;
    }
}

AttributeContext::AttributeContext(const IAttributeManager & manager) :
    _manager(manager),
    _attributes(),
    _enumAttributes(),
    _cacheLock()
{ }

AttributeContext::~AttributeContext() { }

const IAttributeVector *
AttributeContext::getAttribute(const string & name) const
{
    vespalib::LockGuard guard(_cacheLock);
    return getAttribute(_attributes, name, false);
}

const IAttributeVector *
AttributeContext::getAttributeStableEnum(const string & name) const
{
    vespalib::LockGuard guard(_cacheLock);
    return getAttribute(_enumAttributes, name, true);
}

void AttributeContext::releaseEnumGuards() {
    vespalib::LockGuard guard(_cacheLock);
    _enumAttributes.clear();
}

void
AttributeContext::getAttributeList(std::vector<const IAttributeVector *> & list) const
{
    std::vector<AttributeGuard> attributes;
    _manager.getAttributeList(attributes);
    for (size_t i = 0; i < attributes.size(); ++i) {
        list.push_back(getAttribute(attributes[i]->getName()));
    }
}

} // namespace search
