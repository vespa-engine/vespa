// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "requestcontext.h"
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/exceptions.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.matching.requestcontext");

namespace proton {

using search::attribute::IAttributeVector;

RequestContext::RequestContext(const Doom & softDoom, IAttributeContext & attributeContext,
                               const search::fef::Properties& rank_properties)
    : _softDoom(softDoom),
      _attributeContext(attributeContext),
      _rank_properties(rank_properties)
{
}

const search::attribute::IAttributeVector *
RequestContext::getAttribute(const vespalib::string &name) const
{
    return _attributeContext.getAttribute(name);
}

const search::attribute::IAttributeVector *
RequestContext::getAttributeStableEnum(const vespalib::string &name) const
{
    return _attributeContext.getAttributeStableEnum(name);
}

void
RequestContext::asyncForAttribute(const vespalib::string &name, std::unique_ptr<IAttributeFunctor> func) const
{
    _attributeContext.asyncForAttribute(name, std::move(func));
}

vespalib::eval::Value::UP
RequestContext::get_query_tensor(const vespalib::string& tensor_name) const
{
    auto property = _rank_properties.lookup(tensor_name);
    if (property.found() && !property.get().empty()) {
        const vespalib::string& value = property.get();
        vespalib::nbostream stream(value.data(), value.size());
        try {
            return vespalib::tensor::DefaultTensorEngine::ref().decode(stream);
        } catch (vespalib::IllegalArgumentException& ex) {
            LOG(warning, "Query tensor '%s' could not be deserialized", tensor_name.c_str());
            return vespalib::eval::Value::UP();
        }
    }
    return vespalib::eval::Value::UP();
}

}
