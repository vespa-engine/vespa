// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/vespalib/eval/value.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/tensor/default_tensor.h>

namespace search {
namespace attribute { class TensorAttribute; }
namespace features {

class TensorAttributeExecutor : public fef::FeatureExecutor
{
private:
    const search::attribute::TensorAttribute *_attribute;
    vespalib::eval::TensorValue::UP _tensor;
    vespalib::eval::TensorValue::UP _emptyTensor;

public:
    TensorAttributeExecutor(const search::attribute::TensorAttribute *attribute);
    virtual void execute(fef::MatchData &data);
};

} // namespace features
} // namespace search
