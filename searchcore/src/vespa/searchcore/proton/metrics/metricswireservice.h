// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>
#include <vector>

namespace proton {

class AttributeMetrics;
class IndexMetrics;
struct DocumentDBTaggedMetrics;

struct MetricsWireService {
    MetricsWireService();
    virtual ~MetricsWireService();
    virtual void set_attributes(AttributeMetrics& subAttributes, std::vector<std::string> field_names) = 0;
    virtual void set_index_fields(IndexMetrics& index_fields, std::vector<std::string> field_names) = 0;
    virtual void addRankProfile(DocumentDBTaggedMetrics& owner, const std::string& name, size_t numDocIdPartitions) = 0;
    virtual void cleanRankProfiles(DocumentDBTaggedMetrics& owner) = 0;
};

}
