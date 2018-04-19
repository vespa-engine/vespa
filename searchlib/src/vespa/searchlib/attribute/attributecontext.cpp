// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributecontext.h"
#include "attributevector.h"
#include "attribute_read_guard.h"
#include <vespa/vespalib/stllike/hash_map.hpp>

using namespace search;
using namespace search::attribute;

namespace search {

const IAttributeVector *
AttributeContext::getAttribute(AttributeMap & map, const string & name, bool stableEnum) const
{
    AttributeMap::const_iterator itr = map.find(name);
    if (itr != map.end()) {
        if (itr->second) {
            return itr->second->attribute();
        } else {
            return nullptr;
        }
    } else {
        auto readGuard = _manager.getAttributeReadGuard(name, stableEnum);
        const IAttributeVector *attribute = nullptr;
        if (readGuard) {
            attribute = readGuard->attribute();
        }
        map[name] = std::move(readGuard);
        return attribute;
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
    std::lock_guard<std::mutex> guard(_cacheLock);
    return getAttribute(_attributes, name, false);
}

const IAttributeVector *
AttributeContext::getAttributeStableEnum(const string & name) const
{
    std::lock_guard<std::mutex> guard(_cacheLock);
    return getAttribute(_enumAttributes, name, true);
}

void AttributeContext::releaseEnumGuards() {
    std::lock_guard<std::mutex> guard(_cacheLock);
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
