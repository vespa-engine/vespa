// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "idstring.h"
#include "idstringexception.h"
#include <vespa/document/bucket/bucketid.h>
#include <vespa/vespalib/util/md5.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <cstring>
#include <charconv>

using vespalib::string;
using vespalib::stringref;
using vespalib::make_string;

namespace document {

VESPA_IMPLEMENT_EXCEPTION(IdParseException, vespalib::Exception);

namespace {

void reportError(const char* part) __attribute__((noinline));
void reportTooShortDocId(const char * id, size_t sz) __attribute__((noinline));
void reportNoSchemeSeparator(const char * id) __attribute__((noinline));
void reportNoId(const char * id) __attribute__((noinline));

void
reportError(const char* part) {
    throw IdParseException(make_string("Unparseable id: No %s separator ':' found", part), VESPA_STRLOC);
}

void
reportNoSchemeSeparator(const char * id) {
    throw IdParseException(make_string("Unparseable id '%s': No scheme separator ':' found", id), VESPA_STRLOC);
}

void
reportNoId(const char * id){
    throw IdParseException(make_string("Unparseable id '%s': No 'id:' found", id), VESPA_STRLOC);
}

void
reportTooShortDocId(const char * id, size_t sz) {
    throw IdParseException( make_string( "Unparseable id '%s': It is too short(%li) " "to make any sense", id, sz), VESPA_STRLOC);
}

union TwoByte {
    char     asChar[2];
    uint16_t as16;
};

union FourByte {
    char     asChar[4];
    uint32_t as32;
};

constexpr FourByte G_null = {{'n', 'u', 'l', 'l'}};
constexpr TwoByte G_id = {{'i', 'd'}};

const char *
fmemchr(const char * s, const char * e) noexcept {
    return static_cast<const char *>(memchr(s, ':', e - s));
}

// Avoid issues with primitive alignment when reading from buffer.
// Requires caller to ensure buffer is big enough to read from.
template <typename T>
constexpr T read_unaligned(const char* buf) noexcept
{
    T tmp;
    memcpy(&tmp, buf, sizeof(T));
    return tmp;
}

void
verifyIdString(const char * id, size_t sz_)
{
    if (sz_ > 4) [[likely]] {
        if ((G_id.as16 == read_unaligned<uint16_t>(id)) && (id[2] == ':')) [[likely]] {
            return;
        } else if ((sz_ == 6) && (G_null.as32 == read_unaligned<uint32_t>(id)) && (id[4] == ':') && (id[5] == ':')) {
            reportNoId(id);
        } else if (sz_ > 8) {
            reportNoSchemeSeparator(id);
        } else {
            reportTooShortDocId(id, 8);
        }
    } else {
        reportTooShortDocId(id, 5);
    }
}

void
validate(uint16_t numComponents)
{
    if (numComponents < 2) {
        reportError("namespace");
    }
    if (numComponents < 3) {
        reportError("document type");
    }
    if (numComponents < 4) {
        reportError("key/value-pairs");
    }
}


constexpr uint32_t NAMESPACE_OFFSET = 3;

constexpr vespalib::stringref DEFAULT_ID("id::::", 6);

union LocationUnion {
    uint8_t _key[16];
    IdString::LocationType _location[2];
};

uint64_t
parseNumber(stringref s) {
    uint64_t n(0);
    auto res = std::from_chars(s.data(), s.data() + s.size(), n, 10);
    if (res.ptr != s.data() + s.size()) [[unlikely]]{
        throw IdParseException("'n'-value must be a 64-bit number. It was " + s, VESPA_STRLOC);
    }
    if (res.ec == std::errc::result_out_of_range) [[unlikely]] {
        throw IdParseException("'n'-value out of range (" + s + ")", VESPA_STRLOC);
    }
    return n;
}

void
setLocation(IdString::LocationType &loc, IdString::LocationType val,
                 bool &has_set_location, stringref key_values) {
    if (has_set_location) [[unlikely]] {
        throw IdParseException("Illegal key combination in " + key_values);
    }
    loc = val;
    has_set_location = true;
}


}  // namespace

const IdString::Offsets IdString::Offsets::DefaultID(DEFAULT_ID);

IdString::Offsets::Offsets(stringref id) noexcept
    : _offsets()
{
    compute(id);
}

uint16_t
IdString::Offsets::compute(stringref id)
{
    _offsets[0] = NAMESPACE_OFFSET;
    size_t index(1);
    const char * s(id.data() + NAMESPACE_OFFSET);
    const char * e(id.data() + id.size());
    for(s=fmemchr(s, e);
        (s != nullptr) && (index < MAX_COMPONENTS);
        s = fmemchr(s+1, e))
    {
        _offsets[index++] = s - id.data() + 1;
    }
    uint16_t numComponents = index;
    for (;index < VESPA_NELEMS(_offsets); index++) {
        _offsets[index] = id.size() + 1; // 1 is added due to the implicitt accounting for ':'
    }
    return numComponents;
}

IdString::LocationType
IdString::makeLocation(stringref s) {
    LocationUnion location;
    fastc_md5sum(reinterpret_cast<const unsigned char*>(s.data()), s.size(), location._key);
    return location._location[0];
}

IdString::IdString()
    : _rawId(DEFAULT_ID),
      _location(0),
      _offsets(Offsets::DefaultID),
      _groupOffset(0),
      _has_number(false)
{
}

IdString::IdString(stringref id)
    : _rawId(id),
      _location(0),
      _offsets(),
      _groupOffset(0),
      _has_number(false)
{
    // TODO(magnarn): Require that keys are lexicographically ordered.
    verifyIdString(id.data(), id.size());
    validate(_offsets.compute(id));

    stringref key_values(getComponent(2));
    char key(0);
    string::size_type pos = 0;
    bool has_set_location = false;
    bool hasFoundKey(false);
    for (string::size_type i = 0; i < key_values.size(); ++i) {
        if (!hasFoundKey && (key_values[i] == '=')) {
            key = key_values[i-1];
            pos = i + 1;
            hasFoundKey = true;
        } else if (key_values[i] == ',' || i == key_values.size() - 1) {
            stringref value(key_values.substr(pos, i - pos + (i == key_values.size() - 1)));
            if (key == 'n') {
                char tmp=value[value.size()];
                const_cast<char &>(value[value.size()]) = 0;
                setLocation(_location, parseNumber(value), has_set_location, key_values);
                _has_number = true;
                const_cast<char &>(value[value.size()]) = tmp;
            } else if (key == 'g') {
                setLocation(_location, makeLocation(value), has_set_location, key_values);
                _groupOffset = offset(2) + pos;
            } else {
                throw IdParseException(make_string("Illegal key '%c'", key));
            }
            pos = i + 1;
            hasFoundKey = false;
        }
    }

    if (!has_set_location) {
        _location = makeLocation(getNamespaceSpecific());
    }
}

} // document
