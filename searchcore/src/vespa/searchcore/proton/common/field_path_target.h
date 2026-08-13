// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <memory>
#include <string>
#include <string_view>

namespace document {
class DocumentType;
}

namespace proton {

/**
 * A parsed FieldPath for partial update of attribute vector using
 * field paths.
 */
class FieldPathTarget {
public:
    using DocumentType = document::DocumentType;

    enum class Kind : uint8_t {
        UNSUPPORTED = 0,
        ARRAY_INDEX,
    };

private:
    Kind        _kind;
    std::string _attribute_name;
    uint32_t    _index;

    FieldPathTarget() : _kind(Kind::UNSUPPORTED), _attribute_name(), _index(0) {}

public:
    [[nodiscard]] Kind kind() const noexcept { return _kind; }
    [[nodiscard]] std::string_view attribute_name() const noexcept { return _attribute_name; }
    [[nodiscard]] uint32_t index() const noexcept { return _index; }

    [[nodiscard]] bool is_unsupported() const noexcept { return _kind == Kind::UNSUPPORTED; }

    static FieldPathTarget unsupported();
    static FieldPathTarget array_index(std::string attribute_name, uint32_t index);

    // parse does not throw, malformed or unsupported field paths become Kind::UNSUPPORTED.
    static FieldPathTarget parse(const std::string& original_field_path, const DocumentType& doc_type);
};

} // namespace proton
