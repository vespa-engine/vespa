// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace proton {

class AttributeMetrics;

/**
 * A collection of references to all the metrics for a set of attributes.
 */
class AttributeMetricsCollection
{
private:
    AttributeMetrics &_metrics;

public:
    AttributeMetricsCollection(AttributeMetrics &metrics)
        : _metrics(metrics)
    {
    }
    AttributeMetrics &getMetrics() const { return _metrics; }
};

}

