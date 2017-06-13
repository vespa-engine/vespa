// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace proton {

class AttributeMetrics;
class LegacyAttributeMetrics;

/**
 * A collection of references to all the metrics for a set of attributes.
 */
class AttributeMetricsCollection
{
private:
    AttributeMetrics &_metrics;
    LegacyAttributeMetrics &_legacyMetrics;

public:
    AttributeMetricsCollection(AttributeMetrics &metrics,
                               LegacyAttributeMetrics &legacyMetrics)
        : _metrics(metrics),
          _legacyMetrics(legacyMetrics)
    {
    }
    AttributeMetrics &getMetrics() const { return _metrics; }
    LegacyAttributeMetrics &getLegacyMetrics() const { return _legacyMetrics; }
};

} // namespace proton

