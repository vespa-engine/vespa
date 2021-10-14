// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "current_samples.h"

namespace vespalib {
namespace metrics {

using Guard = std::lock_guard<std::mutex>;

void
CurrentSamples::add(Counter::Increment inc)
{
    Guard guard(lock);
    counterIncrements.add(inc);
}

void
CurrentSamples::sample(Gauge::Measurement value)
{
    Guard guard(lock);
    gaugeMeasurements.add(value);
}

void
CurrentSamples::extract(CurrentSamples &into)
{
    Guard guard(lock);
    swap(into.counterIncrements, counterIncrements);
    swap(into.gaugeMeasurements, gaugeMeasurements);
}

} // namespace vespalib::metrics
} // namespace vespalib
