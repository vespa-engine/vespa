// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mock_attribute_context.h"

namespace search::attribute::test {

const IAttributeVector *
MockAttributeContext::get(const string &name) const {
    if (_vectors.find(name) == _vectors.end()) {
        return 0;
    }
    return _vectors.find(name)->second;
}
const IAttributeVector *
MockAttributeContext::getAttribute(const string &name) const {
    return get(name);
}
const IAttributeVector *
MockAttributeContext::getAttributeStableEnum(const string &name) const {
    return get(name);
}
void
MockAttributeContext::getAttributeList(std::vector<const IAttributeVector *> & list) const {
    Map::const_iterator pos = _vectors.begin();
    Map::const_iterator end = _vectors.end();
    for (; pos != end; ++pos) {
        list.push_back(pos->second);
    }
}
MockAttributeContext::~MockAttributeContext() {
    Map::iterator pos = _vectors.begin();
    Map::iterator end = _vectors.end();
    for (; pos != end; ++pos) {
        delete pos->second;
    }
}

void
MockAttributeContext::add(IAttributeVector *attr) {
    _vectors[attr->getName()] = attr;
}

void
MockAttributeContext::asyncForAttribute(const vespalib::string &, std::unique_ptr<IAttributeFunctor>) const {
    throw std::runtime_error("MockAttributeContext::asyncForAttribute is not implemented and should not be reached");
}

}
