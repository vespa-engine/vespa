// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "metricswireservice.h"

namespace proton {

/*
 * Dummy version of metrics wire service.
 */
struct DummyWireService : public MetricsWireService {
    DummyWireService();
    ~DummyWireService() override;
    void addAttribute(AttributeMetrics&, const std::string&) override;
    void removeAttribute(AttributeMetrics&, const std::string&) override;
    void cleanAttributes(AttributeMetrics&) override;
    void addRankProfile(DocumentDBTaggedMetrics&, const std::string&, size_t) override;
    void cleanRankProfiles(DocumentDBTaggedMetrics&) override;
};

}
