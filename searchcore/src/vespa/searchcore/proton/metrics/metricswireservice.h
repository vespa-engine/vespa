// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>

namespace proton {

class AttributeMetrics;
struct DocumentDBTaggedMetrics;

struct MetricsWireService {
    virtual void addAttribute(AttributeMetrics &subAttributes,
                              const std::string &name) = 0;
    virtual void removeAttribute(AttributeMetrics &subAttributes,
                                 const std::string &name) = 0;
    virtual void cleanAttributes(AttributeMetrics &subAttributes) = 0;
    virtual void addRankProfile(DocumentDBTaggedMetrics &owner,
                                const std::string &name,
                                size_t numDocIdPartitions) = 0;
    virtual void cleanRankProfiles(DocumentDBTaggedMetrics &owner) = 0;
    virtual ~MetricsWireService() {}
};

struct DummyWireService : public MetricsWireService {
    virtual void addAttribute(AttributeMetrics &, const std::string &) override {}
    virtual void removeAttribute(AttributeMetrics &, const std::string &) override {}
    virtual void cleanAttributes(AttributeMetrics &) override {}
    virtual void addRankProfile(DocumentDBTaggedMetrics &, const std::string &, size_t) override {}
    virtual void cleanRankProfiles(DocumentDBTaggedMetrics &) override {}
};

}

