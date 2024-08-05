// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributecontext.h"
#include "attributevector.h"
#include "attribute_read_guard.h"
#include <vespa/vespalib/stllike/hash_map.hpp>

using namespace search::attribute;

namespace search {

const IAttributeVector *
AttributeContext::getAttribute(AttributeMap & map, std::string_view name, bool stableEnum) const
{
    auto itr = map.find(name);
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
        map[vespalib::string(name)] = std::move(readGuard);
        return attribute;
    }
}

const IAttributeVector *
AttributeContext::getAttributeMtSafe(AttributeMap &map, std::string_view name, bool stableEnum) const {
    std::lock_guard<std::mutex> guard(_cacheLock);
    return getAttribute(map, name, stableEnum);
}

AttributeContext::AttributeContext(const IAttributeManager & manager)
    : _manager(manager),
      _mtSafe(false),
      _attributes(),
      _enumAttributes(),
      _cacheLock()
{ }

AttributeContext::~AttributeContext() = default;

const IAttributeVector *
AttributeContext::getAttribute(std::string_view name) const
{
    return _mtSafe
        ? getAttributeMtSafe(_attributes, name, false)
        : getAttribute(_attributes, name, false);
}

const IAttributeVector *
AttributeContext::getAttributeStableEnum(std::string_view name) const
{
    return _mtSafe
           ? getAttributeMtSafe(_enumAttributes, name, true)
           : getAttribute(_enumAttributes, name, true);
}

void AttributeContext::releaseEnumGuards() {
    if (_mtSafe) {
        std::lock_guard<std::mutex> guard(_cacheLock);
        _enumAttributes.clear();
    } else {
        _enumAttributes.clear();
    }
}

void
AttributeContext::getAttributeList(std::vector<const IAttributeVector *> & list) const
{
    std::vector<AttributeGuard> attributes;
    _manager.getAttributeList(attributes);
    for (auto & attribute : attributes) {
        list.push_back(getAttribute(attribute->getName()));
    }
}

void
AttributeContext::asyncForAttribute(std::string_view name, std::unique_ptr<IAttributeFunctor> func) const {
    _manager.asyncForAttribute(name, std::move(func));
}

} // namespace search
