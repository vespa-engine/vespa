// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_access_recorder.h"
#include <vespa/vespalib/stllike/hash_set.hpp>

using search::attribute::IAttributeVector;

namespace streaming {

AttributeAccessRecorder::AttributeAccessRecorder(std::unique_ptr<IAttributeContext> ctx)
    : _ctx(std::move(ctx)),
      _accessed_attributes()
{
}

AttributeAccessRecorder::~AttributeAccessRecorder() = default;

void
AttributeAccessRecorder::asyncForAttribute(const vespalib::string& name, std::unique_ptr<search::attribute::IAttributeFunctor> func) const
{
    _ctx->asyncForAttribute(name, std::move(func));
}

const IAttributeVector*
AttributeAccessRecorder::getAttribute(const string& name) const
{
    auto ret = _ctx->getAttribute(name);
    if (ret != nullptr) {
        _accessed_attributes.insert(name);
    }
    return ret;
}

const IAttributeVector*
AttributeAccessRecorder::getAttributeStableEnum(const string& name) const
{
    auto ret = _ctx->getAttributeStableEnum(name);
    if (ret != nullptr) {
        _accessed_attributes.insert(name);
    }
    return ret;
}

void
AttributeAccessRecorder::getAttributeList(std::vector<const IAttributeVector*>& list) const
{
    _ctx->getAttributeList(list);
}

void
AttributeAccessRecorder::releaseEnumGuards()
{
    _ctx->releaseEnumGuards();
}

std::vector<vespalib::string>
AttributeAccessRecorder::get_accessed_attributes() const
{
    std::vector<vespalib::string> result;
    result.reserve(_accessed_attributes.size());
    for (auto& attr : _accessed_attributes) {
        result.emplace_back(attr);
    }
    return result;
}

}
