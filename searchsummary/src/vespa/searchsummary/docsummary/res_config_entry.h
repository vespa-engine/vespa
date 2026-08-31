// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <span>
#include <string>

namespace search::docsummary {

class DocsumFieldWriter;
class SlimeFillerFilter;
class SummaryElementsSelector;

/**
 * This struct describes a single docsum field (name and type).
 **/
class ResConfigEntry {
private:
    std::string                              _name;
    std::unique_ptr<SummaryElementsSelector> _elements_selector;
    std::unique_ptr<DocsumFieldWriter>       _writer;
    std::unique_ptr<SlimeFillerFilter>       _struct_fields_filter;
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
     * The filter matching the struct sub-fields to include in the output, or nullptr when all of them
     * are. Built from the names in the summary config, which are relative to this field, e.g.
     * "value.s2a" for a map of struct. Owned rather than referenced, since the summary config it comes
     * from need not outlive this.
     */
    const SlimeFillerFilter* struct_fields_filter() const noexcept { return _struct_fields_filter.get(); }
    DocsumFieldWriter* writer() const noexcept { return _writer.get(); }
    const SummaryElementsSelector& elements_selector() const noexcept { return *_elements_selector; };
    bool is_generated() const { return _generated; }
};

} // namespace search::docsummary
