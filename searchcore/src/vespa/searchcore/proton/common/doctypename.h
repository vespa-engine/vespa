// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>

namespace search::engine { class Request; }
namespace document { class DocumentType; }

namespace proton {

class DocTypeName
{
    std::string _name;

public:
    DocTypeName() noexcept : _name() { }
    explicit DocTypeName(std::string_view name) noexcept : _name(name) { }
    explicit DocTypeName(const search::engine::Request &request) noexcept;
    explicit DocTypeName(const document::DocumentType &docType) noexcept;

    const std::string & getName() const { return _name; }

    bool operator<(const DocTypeName &rhs) const {
        return _name < rhs._name;
    }

    const std::string & toString() const { return _name; }
};


} // namespace proton

