// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_factory_blueprint.h"
#include <vespa/eval/eval/function.h>

#include <vespa/log/log.h>
LOG_SETUP(".features.tensor_factory_blueprint");

using namespace search::fef;
using vespalib::eval::Function;

namespace search {
namespace features {

vespalib::string TensorFactoryBlueprint::ATTRIBUTE_SOURCE = "attribute";
vespalib::string TensorFactoryBlueprint::QUERY_SOURCE = "query";

bool
TensorFactoryBlueprint::extractSource(const vespalib::string &source)
{
    vespalib::string error;
    bool unwrapOk = Function::unwrap(source, _sourceType, _sourceParam, error);
    if (!unwrapOk) {
        LOG(error, "Failed to extract source param: '%s'", error.c_str());
        return false;
    }
    if (_sourceType != ATTRIBUTE_SOURCE && _sourceType != QUERY_SOURCE) {
        LOG(error, "Expected source type '%s' or '%s', but it was '%s'",
                ATTRIBUTE_SOURCE.c_str(), QUERY_SOURCE.c_str(), _sourceType.c_str());
        return false;
    }
    return true;
}

TensorFactoryBlueprint::TensorFactoryBlueprint(const vespalib::string &baseName)
    : Blueprint(baseName),
      _sourceType(),
      _sourceParam(),
      _dimension("0"), // default dimension is set to the source param if not specified.
      _valueType(vespalib::eval::ValueType::error_type())
{
}

TensorFactoryBlueprint::~TensorFactoryBlueprint() {}

} // namespace features
} // namespace search
