// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "parameterdescriptions.h"

namespace search {
namespace fef {

ParameterDescriptions::Description::Description(size_t tag) :
    _tag(tag),
    _params(),
    _repeat(0)
{ }

ParameterDescriptions::Description::~Description() { }

ParameterDescriptions::Description &
ParameterDescriptions::Description::addParameter(const ParamDescItem &param) {
    _params.push_back(param);
    return *this;
}

ParamDescItem
ParameterDescriptions::Description::getParam(size_t i) const
{
    if (i < _params.size()) {
        return _params[i];
    }
    size_t offset = (i - _params.size()) % _repeat;
    size_t realIndex = _params.size() - _repeat + offset;
    return _params[realIndex];
}

ParameterDescriptions::ParameterDescriptions() :
    _descriptions(),
    _nextTag(0)
{ }

ParameterDescriptions::~ParameterDescriptions() { }

ParameterDescriptions &
ParameterDescriptions::desc() {
    _descriptions.push_back(Description(_nextTag++));
    return *this;
}

ParameterDescriptions &
ParameterDescriptions::desc(size_t tag) {
    _descriptions.push_back(Description(tag));
    _nextTag = tag + 1;
    return *this;
}

} // namespace fef
} // namespace search
