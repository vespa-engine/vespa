// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>

namespace proton {
class AttributeMetricsCollection;
class DocumentDBMetricsCollection;
class LegacyAttributeMetrics;

struct MetricsWireService {
    virtual void addAttribute(const AttributeMetricsCollection &subAttributes,
                              LegacyAttributeMetrics *totalAttributes,
                              const std::string &name) = 0;
    virtual void removeAttribute(const AttributeMetricsCollection &subAttributes,
                                 LegacyAttributeMetrics *totalAttributes,
                                 const std::string &name) = 0;
    virtual void cleanAttributes(const AttributeMetricsCollection &subAttributes,
                                 LegacyAttributeMetrics *totalAttributes) = 0;
    virtual void addRankProfile(DocumentDBMetricsCollection &owner,
                                const std::string &name,
                                size_t numDocIdPartitions) = 0;
    virtual void cleanRankProfiles(DocumentDBMetricsCollection &owner) = 0;
    virtual ~MetricsWireService() {}
};

struct DummyWireService : public MetricsWireService {
    virtual void addAttribute(const AttributeMetricsCollection &, LegacyAttributeMetrics *, const std::string &) override {}
    virtual void removeAttribute(const AttributeMetricsCollection &, LegacyAttributeMetrics *, const std::string &) override {}
    virtual void cleanAttributes(const AttributeMetricsCollection &, LegacyAttributeMetrics *) override {}
    virtual void addRankProfile(DocumentDBMetricsCollection &, const std::string &, size_t) override {}
    virtual void cleanRankProfiles(DocumentDBMetricsCollection &) override {}
};

}  // namespace proton

