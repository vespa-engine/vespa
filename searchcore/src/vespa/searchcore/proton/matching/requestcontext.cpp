// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "requestcontext.h"
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/fef/iqueryenvironment.h>
#include <vespa/searchlib/fef/query_value.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/issue.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.matching.requestcontext");

using vespalib::eval::FastValueBuilderFactory;
using vespalib::Issue;

namespace proton {

using search::attribute::IAttributeVector;

RequestContext::RequestContext(const Doom & doom, IAttributeContext & attributeContext,
                               const search::fef::IQueryEnvironment& query_env,
                               search::fef::IObjectStore& shared_store,
                               const search::attribute::AttributeBlueprintParams& attribute_blueprint_params)
    : _doom(doom),
      _attributeContext(attributeContext),
      _query_env(query_env),
      _shared_store(shared_store),
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

const vespalib::eval::Value*
RequestContext::get_query_tensor(const vespalib::string& tensor_name) const
{
    try {
        auto value = search::fef::QueryValue::from_config(tensor_name, _query_env.getIndexEnvironment());
        value.prepare_shared_state(_query_env, _shared_store);
        return value.lookup_value(_shared_store);
    } catch (const search::fef::InvalidValueTypeException& ex) {
        Issue::report("Invalid type '%s' for query tensor '%s'",
                      ex.getMessage().c_str(), tensor_name.c_str());
        return nullptr;
    }
}

const search::attribute::AttributeBlueprintParams&
RequestContext::get_attribute_blueprint_params() const
{
    return _attribute_blueprint_params;
}

}
