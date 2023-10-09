// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/metrics/handle.h>

namespace metrics {

struct MetricNameIdTag {};
struct DescriptionIdTag {};
struct TagKeyIdTag {};
struct TagValueIdTag {};

using MetricNameId = vespalib::metrics::Handle<MetricNameIdTag>;
using DescriptionId = vespalib::metrics::Handle<DescriptionIdTag>;
using TagKeyId = vespalib::metrics::Handle<TagKeyIdTag>;
using TagValueId = vespalib::metrics::Handle<TagValueIdTag>;

struct NameRepo {
    static MetricNameId metricId(const vespalib::string &name);
    static DescriptionId descriptionId(const vespalib::string &name);
    static TagKeyId tagKeyId(const vespalib::string &name);
    static TagValueId tagValueId(const vespalib::string &value);

    static const vespalib::string& metricName(MetricNameId id);
    static const vespalib::string& description(DescriptionId id);
    static const vespalib::string& tagKey(TagKeyId id);
    static const vespalib::string& tagValue(TagValueId id);
};

} // metrics

