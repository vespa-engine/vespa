// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "repo.h"
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
Repo::metricId(const vespalib::string &name)
{
    size_t id = metricNames.resolve(name);
    return MetricNameId(id);
}

DescriptionId
Repo::descriptionId(const vespalib::string &name)
{
    size_t id = descriptions.resolve(name);
    return DescriptionId(id);
}

TagKeyId
Repo::tagKey(const vespalib::string &name)
{
    size_t id = tagKeys.resolve(name);
    return TagKeyId(id);
}

TagValueId
Repo::tagValue(const vespalib::string &value)
{
    size_t id = tagValues.resolve(value);
    return TagValueId(id);
}

const vespalib::string&
Repo::metricName(MetricNameId id)
{
    return metricNames.lookup(id.id());
}

const vespalib::string&
Repo::description(DescriptionId id)
{
    return descriptions.lookup(id.id());
}

const vespalib::string&
Repo::tagKey(TagKeyId id)
{
    return tagKeys.lookup(id.id());
}

const vespalib::string&
Repo::tagValue(TagValueId id)
{
    return tagValues.lookup(id.id());
}


} // namespace metrics

