// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/base/field.h>

#include <cstdint>
#include <string_view>

namespace document {
class DocumentType;
class FieldPathUpdate;
} // namespace document

namespace proton {

/**
 * A classified field path update target for partial update of an
 * attribute vector. The kind combines the addressed element with the
 * update operation; anything that cannot be applied to an attribute
 * vector is UNSUPPORTED. The top-level field is kept whenever it
 * resolves, also for UNSUPPORTED targets.
 */
class FieldPathTarget {
public:
    using DocumentType = document::DocumentType;
    using Field = document::Field;
    using FieldPathUpdate = document::FieldPathUpdate;

    enum class Kind : uint8_t {
        UNSUPPORTED = 0,
        ASSIGN_ELEMENT,
    };

private:
    Kind     _kind;
    Field    _field;
    uint32_t _index;

    FieldPathTarget(Kind kind, Field field, uint32_t index) : _kind(kind), _field(std::move(field)), _index(index) {}

public:
    [[nodiscard]] Kind kind() const noexcept { return _kind; }
    [[nodiscard]] const Field* field() const noexcept { return _field.valid() ? &_field : nullptr; }
    [[nodiscard]] std::string_view attribute_name() const noexcept { return _field.getName(); }
    [[nodiscard]] uint32_t index() const noexcept { return _index; }

    [[nodiscard]] bool is_unsupported() const noexcept { return _kind == Kind::UNSUPPORTED; }

    static FieldPathTarget unsupported(Field field);
    static FieldPathTarget assign_element(Field field, uint32_t index);

    // parse does not throw, malformed or unsupported field paths become Kind::UNSUPPORTED.
    static FieldPathTarget parse(const FieldPathUpdate& update, const DocumentType& doc_type);
};

} // namespace proton
