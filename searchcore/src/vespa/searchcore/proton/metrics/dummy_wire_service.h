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
    void set_attributes(AttributeMetrics& subAttributes, std::vector<std::string> field_names) override;
    void set_index_fields(IndexMetrics& index_fields, std::vector<std::string> field_names) override;
    void addRankProfile(DocumentDBTaggedMetrics&, const std::string&, size_t) override;
    void cleanRankProfiles(DocumentDBTaggedMetrics&) override;
};

}
