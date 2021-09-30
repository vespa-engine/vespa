// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "requestcontext.h"
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/exceptions.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.matching.requestcontext");

using vespalib::eval::FastValueBuilderFactory;

namespace proton {

using search::attribute::IAttributeVector;

RequestContext::RequestContext(const Doom & doom, IAttributeContext & attributeContext,
                               const search::fef::Properties& rank_properties,
                               const search::attribute::AttributeBlueprintParams& attribute_blueprint_params)
    : _doom(doom),
      _attributeContext(attributeContext),
      _rank_properties(rank_properties),
      _attribute_blueprint_params(attribute_blueprint_params)
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
            return decode_value(stream, FastValueBuilderFactory::get());
        } catch (vespalib::Exception& ex) {
            LOG(warning, "Query tensor '%s' could not be deserialized: %s",
                tensor_name.c_str(), ex.getMessage().c_str());
            return {};
        }
    }
    return {};
}

const search::attribute::AttributeBlueprintParams&
RequestContext::get_attribute_blueprint_params() const
{
    return _attribute_blueprint_params;
}

}
