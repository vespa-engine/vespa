// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/memory.h>
#include <vespa/vespalib/stllike/string.h>
#include <cstdint>

namespace document {

/**
 * \class document::IdString
 * \ingroup base
 *
 * \brief Scheme for document id.
 *
 * Document id with a scheme that both supports uniform hash based distribution,
 * and location based distribution based on numeric id or textual group..
 */
class IdString {
public:
    using LocationType = uint64_t;
    static LocationType makeLocation(std::string_view s);

    explicit IdString(std::string_view ns);
    IdString();

    [[nodiscard]] std::string_view getNamespace() const { return getComponent(0); }
    [[nodiscard]] bool hasDocType() const { return size(1) != 0; }
    [[nodiscard]] std::string_view getDocType() const  { return getComponent(1); }
    [[nodiscard]] LocationType getLocation() const  { return _location; }
    [[nodiscard]] bool hasNumber() const  { return _has_number; }
    [[nodiscard]] uint64_t getNumber() const  { return _location; }
    [[nodiscard]] bool hasGroup() const  { return _groupOffset != 0; }
    [[nodiscard]] std::string_view getGroup() const  {
        return {getRawId().c_str() + _groupOffset, size_t(offset(3) - _groupOffset - 1)};
    }
    [[nodiscard]] std::string_view getNamespaceSpecific() const {
        return {_rawId.c_str() + offset(3), _rawId.size() - offset(3)};
    }

    bool operator==(const IdString& other) const
        { return toString() == other.toString(); }

    [[nodiscard]] const vespalib::string & toString() const { return _rawId; }

private:
    [[nodiscard]] uint16_t offset(uint32_t index) const { return _offsets[index]; }
    [[nodiscard]] uint16_t size(uint32_t index) const { return std::max(0, int(offset(index+1)) - int(offset(index)) - 1); }
    [[nodiscard]] std::string_view getComponent(size_t index) const { return {_rawId.c_str() + offset(index), size(index)}; }
    [[nodiscard]] const vespalib::string & getRawId() const { return _rawId; }

    class Offsets {
    public:
        Offsets() noexcept = default;
        VESPA_DLL_LOCAL uint16_t compute(std::string_view id);
        uint16_t operator [] (size_t i) const { return _offsets[i]; }
        static const Offsets DefaultID;
    private:
        static constexpr uint32_t MAX_COMPONENTS = 4;
        VESPA_DLL_LOCAL explicit Offsets(std::string_view id) noexcept;
        uint16_t _offsets[MAX_COMPONENTS];
    };

    vespalib::string _rawId;
    LocationType     _location;
    Offsets          _offsets;
    uint16_t         _groupOffset;
    bool             _has_number;
};

} // document
