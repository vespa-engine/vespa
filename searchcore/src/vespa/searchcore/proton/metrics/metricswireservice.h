// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>

namespace proton {

class AttributeMetrics;
class IndexMetrics;
struct DocumentDBTaggedMetrics;

struct MetricsWireService {
    MetricsWireService();
    virtual ~MetricsWireService();
    virtual void addAttribute(AttributeMetrics& subAttributes, const std::string& name) = 0;
    virtual void removeAttribute(AttributeMetrics& subAttributes, const std::string& name) = 0;
    virtual void cleanAttributes(AttributeMetrics& subAttributes) = 0;
    virtual void add_index_field(IndexMetrics& index_fields, const std::string& field_name) = 0;
    virtual void remove_index_field(IndexMetrics& index_fields, const std::string& field_name) = 0;
    virtual void clean_index_fields(IndexMetrics& index_fields) = 0;
    virtual void addRankProfile(DocumentDBTaggedMetrics& owner, const std::string& name, size_t numDocIdPartitions) = 0;
    virtual void cleanRankProfiles(DocumentDBTaggedMetrics& owner) = 0;
};

}
