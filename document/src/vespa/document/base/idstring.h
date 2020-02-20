// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    typedef uint64_t LocationType;
    static LocationType makeLocation(vespalib::stringref s);

    explicit IdString(vespalib::stringref ns);
    IdString();

    vespalib::stringref getNamespace() const { return getComponent(0); }
    bool hasDocType() const { return size(1) != 0; }
    vespalib::stringref getDocType() const  { return getComponent(1); }
    LocationType getLocation() const  { return _location; }
    bool hasNumber() const  { return _has_number; }
    uint64_t getNumber() const  { return _location; }
    bool hasGroup() const  { return _groupOffset != 0; }
    vespalib::stringref getGroup() const  {
        return vespalib::stringref(getRawId().c_str() + _groupOffset, offset(3) - _groupOffset - 1);
    }
    vespalib::stringref getNamespaceSpecific() const {
        return vespalib::stringref(_rawId.c_str() + offset(3), _rawId.size() - offset(3));
    }

    bool operator==(const IdString& other) const
        { return toString() == other.toString(); }

    const vespalib::string & toString() const { return _rawId; }

private:
    uint16_t offset(uint32_t index) const { return _offsets[index]; }
    uint16_t size(uint32_t index) const { return std::max(0, int(offset(index+1)) - int(offset(index)) - 1); }
    vespalib::stringref getComponent(size_t index) const { return vespalib::stringref(_rawId.c_str() + offset(index), size(index)); }
    const vespalib::string & getRawId() const { return _rawId; }

    class Offsets {
    public:
        Offsets() = default;
        uint16_t compute(vespalib::stringref id);
        uint16_t operator [] (size_t i) const { return _offsets[i]; }
        static const Offsets DefaultID;
    private:
        static constexpr uint32_t MAX_COMPONENTS = 4;
        Offsets(vespalib::stringref id);
        uint16_t _offsets[MAX_COMPONENTS];
    };

    vespalib::string _rawId;
    LocationType     _location;
    Offsets          _offsets;
    uint16_t         _groupOffset;
    bool             _has_number;
};

} // document
