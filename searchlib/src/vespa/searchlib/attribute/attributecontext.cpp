// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributecontext.h"
#include "attributevector.h"
#include "attribute_read_guard.h"
#include <vespa/vespalib/stllike/hash_map.hpp>

using namespace search::attribute;

namespace search {

const IAttributeVector *
AttributeContext::getAttribute(AttributeMap & map, const string & name, bool stableEnum) const
{
    std::unique_lock guard(_cacheLock);
    AttributeMap::const_iterator itr = map.find(name);
    std::shared_future<std::unique_ptr<attribute::AttributeReadGuard>> future_read_guard;
    if (itr != map.end()) {
        future_read_guard = itr->second;
        guard.unlock();
    } else {
        std::promise<std::unique_ptr<attribute::AttributeReadGuard>> promise;
        future_read_guard = promise.get_future().share();
        map[name] = future_read_guard;
        guard.unlock();
        promise.set_value(_manager.getAttributeReadGuard(name, stableEnum));
    }
    auto& read_guard = future_read_guard.get();
    if (read_guard) {
        return read_guard->attribute();
    } else {
        return nullptr;
    }
}

AttributeContext::AttributeContext(const IAttributeManager & manager) :
    _manager(manager),
    _attributes(),
    _enumAttributes(),
    _cacheLock()
{ }

AttributeContext::~AttributeContext() = default;

const IAttributeVector *
AttributeContext::getAttribute(const string & name) const
{
    return getAttribute(_attributes, name, false);
}

const IAttributeVector *
AttributeContext::getAttributeStableEnum(const string & name) const
{
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

void
AttributeContext::asyncForAttribute(const vespalib::string &name, std::unique_ptr<IAttributeFunctor> func) const {
    _manager.asyncForAttribute(name, std::move(func));
}

} // namespace search
