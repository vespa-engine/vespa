// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <span>
#include <string>
#include <vector>

namespace search::docsummary {

class DocsumFieldWriter;
class SummaryElementsSelector;

/**
 * This struct describes a single docsum field (name and type).
 **/
class ResConfigEntry {
private:
    std::string                              _name;
    std::unique_ptr<SummaryElementsSelector> _elements_selector;
    std::unique_ptr<DocsumFieldWriter>       _writer;
    std::vector<std::string>                 _struct_fields;
    bool                                     _generated;

public:
    ResConfigEntry(const std::string& name_in) noexcept;
    ~ResConfigEntry();
    ResConfigEntry(ResConfigEntry&&) noexcept;
    void set_elements_selector(const SummaryElementsSelector& elements_selector_in);
    void set_writer(std::unique_ptr<DocsumFieldWriter> writer_in);
    void set_struct_fields(std::span<const std::string> struct_fields_in);
    const std::string& name() const noexcept { return _name; }

    /**
     * The names of the struct sub-fields to include in the output, relative to this field, e.g.
     * "value.s2a" for a map of struct. An empty vector means all sub-fields are included. Owned rather
     * than referenced, since the summary config it comes from need not outlive this.
     */
    const std::vector<std::string>& struct_fields() const noexcept { return _struct_fields; }
    DocsumFieldWriter* writer() const noexcept { return _writer.get(); }
    const SummaryElementsSelector& elements_selector() const noexcept { return *_elements_selector; };
    bool is_generated() const { return _generated; }
};

} // namespace search::docsummary
