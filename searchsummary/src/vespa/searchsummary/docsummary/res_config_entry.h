// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <string>

namespace search::docsummary {

class DocsumFieldWriter;
class SummaryElementsSelector;

/**
 * This struct describes a single docsum field (name and type).
 **/
class ResConfigEntry {
private:
    std::string _name;
    std::unique_ptr<SummaryElementsSelector> _elements_selector;
    std::unique_ptr<DocsumFieldWriter> _writer;
    bool _generated;
public:
    ResConfigEntry(const std::string& name_in) noexcept;
    ~ResConfigEntry();
    ResConfigEntry(ResConfigEntry&&) noexcept;
    void set_elements_selector(const SummaryElementsSelector& elements_selector_in);
    void set_writer(std::unique_ptr<DocsumFieldWriter> writer_in);
    const std::string& name() const noexcept { return _name; }
    DocsumFieldWriter* writer() const noexcept { return _writer.get(); }
    const SummaryElementsSelector& elements_selector() const noexcept { return *_elements_selector; };
    bool is_generated() const { return _generated; }
};

}
