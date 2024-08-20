// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <string>

namespace search::docsummary {

class DocsumFieldWriter;

/**
 * This struct describes a single docsum field (name and type).
 **/
class ResConfigEntry {
private:
    std::string _name;
    std::unique_ptr<DocsumFieldWriter> _writer;
    bool _generated;
public:
    ResConfigEntry(const std::string& name_in) noexcept;
    ~ResConfigEntry();
    ResConfigEntry(ResConfigEntry&&) noexcept;
    void set_writer(std::unique_ptr<DocsumFieldWriter> writer);
    const std::string& name() const { return _name; }
    DocsumFieldWriter* writer() const { return _writer.get(); }
    bool is_generated() const { return _generated; }
};

}
