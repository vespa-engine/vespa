// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace search::engine { class Request; }
namespace document { class DocumentType; }

namespace proton {

class DocTypeName
{
    vespalib::string _name;

public:
    explicit DocTypeName(const vespalib::string &name) noexcept : _name(name) { }
    explicit DocTypeName(const search::engine::Request &request) noexcept;
    explicit DocTypeName(const document::DocumentType &docType) noexcept;

    const vespalib::string & getName() const { return _name; }

    bool operator<(const DocTypeName &rhs) const {
        return _name < rhs._name;
    }

    vespalib::string toString() const { return _name; }
};


} // namespace proton

