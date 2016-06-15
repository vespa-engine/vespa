// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "featureoverrider.h"

namespace search {
namespace fef {

FeatureOverrider::FeatureOverrider(FeatureExecutor::LP executor, uint32_t outputIdx, feature_t value)
    : _executor(executor),
      _outputIdx(outputIdx),
      _handle(IllegalHandle),
      _value(value)
{
}

void
FeatureOverrider::inputs_done()
{
    for (uint32_t i = 0; i < inputs().size(); ++i) {
        _executor->addInput(inputs()[i]);
    }
    _executor->inputs_done();
}

void
FeatureOverrider::outputs_done()
{
    if (_outputIdx < outputs().size()) {
        _handle = outputs()[_outputIdx];
    }
    for (uint32_t i = 0; i < outputs().size(); ++i) {
        _executor->bindOutput(outputs()[i]);
    }
    _executor->outputs_done();
}

bool
FeatureOverrider::isPure()
{
    return _executor->isPure();
}

void
FeatureOverrider::execute(MatchData &data)
{
    _executor->execute(data);
    if (_handle != IllegalHandle) {
        *data.resolveFeature(_handle) = _value;
    }
}

} // namespace fef
} // namespace search
