// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "parameterdescriptions.h"

namespace search {
namespace fef {

ParameterDescriptions::Description::Description(size_t tag) :
    _tag(tag),
    _params(),
    _repeat(0)
{
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
{
}

} // namespace fef
} // namespace search
