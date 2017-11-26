// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "current_samples.h"

namespace vespalib {
namespace metrics {

void swap(CurrentSamples& a, CurrentSamples& b)
{
    using std::swap;
    swap(a.counterIncrements, b.counterIncrements);
    swap(a.gaugeMeasurements, b.gaugeMeasurements);
}

} // namespace vespalib::metrics
} // namespace vespalib
