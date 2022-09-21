// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <memory>

namespace search::docsummary {

class DocsumFieldWriter;

/**
 * This struct describes a single docsum field (name and type).
 **/
class ResConfigEntry {
private:
    vespalib::string _name;
    std::unique_ptr<DocsumFieldWriter> _writer;
    bool _generated;
public:
    ResConfigEntry(const vespalib::string& name_in) noexcept;
    ~ResConfigEntry();
    ResConfigEntry(ResConfigEntry&&) noexcept;
    void set_writer(std::unique_ptr<DocsumFieldWriter> writer);
    const vespalib::string& name() const { return _name; }
    DocsumFieldWriter* writer() const { return _writer.get(); }
    bool is_generated() const { return _generated; }
};

}
