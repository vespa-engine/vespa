// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>
#include <thread>
#include <vespa/vespalib/stllike/string.h>
#include "name_collection.h"
#include "counter.h"
#include "gauge.h"
#include "current_samples.h"
#include "snapshots.h"
#include "point.h"
#include "point_builder.h"
#include "dimension.h"
#include "label.h"

namespace vespalib {
namespace metrics {


class MetricsManager
    : public std::enable_shared_from_this<MetricsManager>
{
public:
    virtual ~MetricsManager() {}

    virtual Counter counter(const vespalib::string &name) = 0; // get or create
    virtual  Gauge   gauge (const vespalib::string &name) = 0; // get or create

    virtual Dimension dimension(const vespalib::string &name) = 0; // get or create
    virtual Label label(const vespalib::string &value) = 0; // get or create
    PointBuilder pointBuilder() {
        return PointBuilder(shared_from_this());
    }
    virtual PointBuilder pointBuilder(Point from) = 0;

    virtual Point pointFrom(PointMap::BackingMap map) = 0;

    virtual Snapshot snapshot() = 0;
    virtual Snapshot totalSnapshot() = 0;

    // for use from Counter only
    virtual void add(Counter::Increment inc) = 0;

    // for use from Gauge only
    virtual void sample(Gauge::Measurement value) = 0;
};


} // namespace vespalib::metrics
} // namespace vespalib
