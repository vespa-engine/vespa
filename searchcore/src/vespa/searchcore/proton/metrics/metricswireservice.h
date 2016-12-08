// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>

namespace proton {
class LegacyAttributeMetrics;
class LegacyDocumentDBMetrics;

struct MetricsWireService {
    virtual void addAttribute(LegacyAttributeMetrics &subAttributes,
                              LegacyAttributeMetrics *totalAttributes,
                              const std::string &name) = 0;
    virtual void removeAttribute(LegacyAttributeMetrics &subAttributes,
                                 LegacyAttributeMetrics *totalAttributes,
                                 const std::string &name) = 0;
    virtual void cleanAttributes(LegacyAttributeMetrics &subAttributes,
                                 LegacyAttributeMetrics *totalAttributes) = 0;
    virtual void addRankProfile(LegacyDocumentDBMetrics &owner,
                                const std::string &name,
                                size_t numDocIdPartitions) = 0;
    virtual void cleanRankProfiles(LegacyDocumentDBMetrics &owner) = 0;
    virtual ~MetricsWireService() {}
};

struct DummyWireService : public MetricsWireService {
    virtual void addAttribute(LegacyAttributeMetrics &, LegacyAttributeMetrics *, const std::string &) {}
    virtual void removeAttribute(LegacyAttributeMetrics &, LegacyAttributeMetrics *, const std::string &) {}
    virtual void cleanAttributes(LegacyAttributeMetrics &, LegacyAttributeMetrics *) {}
    virtual void addRankProfile(LegacyDocumentDBMetrics &, const std::string &, size_t) {}
    virtual void cleanRankProfiles(LegacyDocumentDBMetrics &) {}
};

}  // namespace proton

