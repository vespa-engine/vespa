// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "blueprint.h"
#include "parametervalidator.h"
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".fef.blueprint");

namespace search::fef {

const FeatureType &
Blueprint::defineInput(vespalib::stringref inName,
                       AcceptInput accept)
{
    assert(_dependency_handler != nullptr);
    return _dependency_handler->resolve_input(inName, accept);
}

void
Blueprint::describeOutput(vespalib::stringref outName,
                          vespalib::stringref desc,
                          const FeatureType &type)
{
    (void) desc;
    assert(_dependency_handler != nullptr);
    _dependency_handler->define_output(outName, type);
}

Blueprint::Blueprint(vespalib::stringref baseName)
    : _baseName(baseName),
      _name(),
      _dependency_handler(nullptr)
{
}

Blueprint::~Blueprint() = default;

ParameterDescriptions
Blueprint::getDescriptions() const
{
    // desc: 0-n parameters
    return ParameterDescriptions().desc().string().repeat();
}

bool
Blueprint::setup(const IIndexEnvironment &indexEnv,
                 const StringVector &params)
{
    ParameterDescriptions descs = getDescriptions();
    ParameterValidator validator(indexEnv, params, descs);
    ParameterValidator::Result result = validator.validate();
    if (result.valid()) {
        return setup(indexEnv, result.getParameters());
    } else {
        LOG(error, "The parameter list used for setting up rank feature %s is not valid: %s",
            getBaseName().c_str(), result.getError().c_str());
        return false;
    }
}

bool
Blueprint::setup(const IIndexEnvironment &indexEnv,
                 const ParameterList &params)
{
    (void) indexEnv; (void) params;
    LOG(error, "The setup function using a typed parameter list does not have a default implementation. "
        "Make sure the setup function is implemented in the rank feature %s.", getBaseName().c_str());
    return false;
}

}
