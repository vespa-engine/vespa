// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "name_repo.h"
#include <vespa/vespalib/metrics/name_collection.h>

using vespalib::metrics::NameCollection;

namespace metrics {

namespace {
NameCollection metricNames;
NameCollection descriptions;
NameCollection tagKeys;
NameCollection tagValues;
}

MetricNameId
NameRepo::metricId(const vespalib::string &name)
{
    size_t id = metricNames.resolve(name);
    return MetricNameId(id);
}

DescriptionId
NameRepo::descriptionId(const vespalib::string &name)
{
    size_t id = descriptions.resolve(name);
    return DescriptionId(id);
}

TagKeyId
NameRepo::tagKeyId(const vespalib::string &name)
{
    size_t id = tagKeys.resolve(name);
    return TagKeyId(id);
}

TagValueId
NameRepo::tagValueId(const vespalib::string &value)
{
    size_t id = tagValues.resolve(value);
    return TagValueId(id);
}

const vespalib::string&
NameRepo::metricName(MetricNameId id)
{
    return metricNames.lookup(id.id());
}

const vespalib::string&
NameRepo::description(DescriptionId id)
{
    return descriptions.lookup(id.id());
}

const vespalib::string&
NameRepo::tagKey(TagKeyId id)
{
    return tagKeys.lookup(id.id());
}

const vespalib::string&
NameRepo::tagValue(TagValueId id)
{
    return tagValues.lookup(id.id());
}


} // namespace metrics

