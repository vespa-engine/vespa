// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>

namespace proton {
class AttributeMetricsCollection;
class LegacyAttributeMetrics;
class LegacyDocumentDBMetrics;

struct MetricsWireService {
    virtual void addAttribute(const AttributeMetricsCollection &subAttributes,
                              LegacyAttributeMetrics *totalAttributes,
                              const std::string &name) = 0;
    virtual void removeAttribute(const AttributeMetricsCollection &subAttributes,
                                 LegacyAttributeMetrics *totalAttributes,
                                 const std::string &name) = 0;
    virtual void cleanAttributes(const AttributeMetricsCollection &subAttributes,
                                 LegacyAttributeMetrics *totalAttributes) = 0;
    virtual void addRankProfile(LegacyDocumentDBMetrics &owner,
                                const std::string &name,
                                size_t numDocIdPartitions) = 0;
    virtual void cleanRankProfiles(LegacyDocumentDBMetrics &owner) = 0;
    virtual ~MetricsWireService() {}
};

struct DummyWireService : public MetricsWireService {
    virtual void addAttribute(const AttributeMetricsCollection &, LegacyAttributeMetrics *, const std::string &) override {}
    virtual void removeAttribute(const AttributeMetricsCollection &, LegacyAttributeMetrics *, const std::string &) override {}
    virtual void cleanAttributes(const AttributeMetricsCollection &, LegacyAttributeMetrics *) override {}
    virtual void addRankProfile(LegacyDocumentDBMetrics &, const std::string &, size_t) override {}
    virtual void cleanRankProfiles(LegacyDocumentDBMetrics &) override {}
};

}  // namespace proton

