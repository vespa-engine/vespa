// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "idstring.h"
#include "idstringexception.h"
#include <vespa/document/bucket/bucketid.h>
#include <vespa/vespalib/util/md5.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/optimized.h>
#include <cerrno>

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

const FourByte _G_null = {{'n', 'u', 'l', 'l'}};
const TwoByte _G_id = {{'i', 'd'}};

typedef char v16qi __attribute__ ((__vector_size__(16)));

v16qi _G_zero  = { ':', ':', ':', ':', ':', ':', ':', ':', ':', ':', ':', ':', ':', ':', ':', ':' };

inline const char *
fmemchr(const char * s, const char * e)
{
    while (s+15 < e) {
#ifdef __clang__
        v16qi tmpCurrent = __builtin_ia32_lddqu(s);
        v16qi tmp0       = tmpCurrent == _G_zero;
#else
        v16qi tmpCurrent = __builtin_ia32_loaddqu(s);
        v16qi tmp0       = __builtin_ia32_pcmpeqb128(tmpCurrent, _G_zero);
#endif
        uint32_t charMap = __builtin_ia32_pmovmskb128(tmp0); // 1 in charMap equals to '\0' in input buffer
        if (__builtin_expect(charMap, 1)) {
            return s + vespalib::Optimized::lsbIdx(charMap);
        }
        s+=16;
    }

    const char c(':');
    while (s+3 < e) {
        if (s[0] == c) {
            return s;
        }
        if (s[1] == c) {
            return s+1;
        }
        if (s[2] == c) {
            return s+2;
        }
        if (s[3] == c) {
            return s+3;
        }
        s+=4;
    }
    while (s < e) {
        if (s[0] == c) {
            return s;
        }
        s++;
    }
    return nullptr;
}

void
verifyIdString(const char * id, size_t sz_)
{
    if (sz_ > 4) {
        if (_G_id.as16 == *reinterpret_cast<const uint16_t *>(id) && id[2] == ':') {
            return;
        } else if ((sz_ == 6) && (_G_null.as32 == *reinterpret_cast<const uint32_t *>(id)) && (id[4] == ':') && (id[5] == ':')) {
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

const vespalib::stringref DEFAULT_ID("id::::");

union LocationUnion {
    uint8_t _key[16];
    IdString::LocationType _location[2];
};

uint64_t parseNumber(stringref number) {
    char* errPos = nullptr;
    errno = 0;
    uint64_t n = strtoul(number.data(), &errPos, 10);
    if (*errPos) {
        throw IdParseException("'n'-value must be a 64-bit number. It was " + number, VESPA_STRLOC);
    }
    if (errno == ERANGE) {
        throw IdParseException("'n'-value out of range (" + number + ")", VESPA_STRLOC);
    }
    return n;
}

void setLocation(IdString::LocationType &loc, IdString::LocationType val,
                 bool &has_set_location, stringref key_values) {
    if (has_set_location) {
        throw IdParseException("Illegal key combination in " + key_values);
    }
    loc = val;
    has_set_location = true;
}


}  // namespace

const IdString::Offsets IdString::Offsets::DefaultID(DEFAULT_ID);

IdString::Offsets::Offsets(stringref id)
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
