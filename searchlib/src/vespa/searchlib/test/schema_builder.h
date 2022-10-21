// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/common/schema.h>
#include <vespa/vespalib/stllike/string.h>
#include <memory>
#include <optional>
#include <vector>

namespace search::test {

class DocBuilder;

/*
 * Class used to make schema based on document type.
 */
class SchemaBuilder {
    const DocBuilder& _doc_builder;
    std::unique_ptr<search::index::Schema> _schema;
    void add_index(vespalib::stringref field_name, std::optional<bool> interleaved_features);
    void add_attribute(vespalib::stringref field_name);
public:
    SchemaBuilder(const DocBuilder& doc_builder);
    ~SchemaBuilder();
    SchemaBuilder& add_indexes(std::vector<vespalib::stringref> field_names, std::optional<bool> interleaved_features = std::nullopt);
    SchemaBuilder& add_all_indexes(std::optional<bool> interleaved_features = std::nullopt);
    SchemaBuilder& add_attributes(std::vector<vespalib::stringref> field_names);
    SchemaBuilder& add_all_attributes();
    search::index::Schema build();
};

}
