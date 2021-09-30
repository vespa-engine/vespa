// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "asciistream.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/memory.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/locale/c.h>
#include <vespa/fastos/file.h>
#include <algorithm>
#include <limits>
#include <stdexcept>
#include <cassert>
#include <cmath>
#include <charconv>

#include <vespa/log/log.h>
LOG_SETUP(".vespalib.stllike.asciistream");

namespace vespalib {

namespace {
    std::vector<string> getPrecisions(const char type) {
        std::vector<string> result(VESPALIB_ASCIISTREAM_MAX_PRECISION + 1);
        for (uint32_t i=0; i<result.size(); ++i) {
            char buf[8];
            int count = snprintf(buf, sizeof(buf), "%%.%u%c", i, type);
            assert(size_t(count) < sizeof(buf));  // Assert no truncation.
            (void) count;
            result[i] = buf;
        }
        return result;
    }
    std::vector<string> fixedPrecisions = getPrecisions('f');
    std::vector<string> scientificPrecisions = getPrecisions('e');
    std::vector<string> autoPrecisions = getPrecisions('g');
}

asciistream &
asciistream::operator << (Precision v) {
    assert(v.getPrecision() <= VESPALIB_ASCIISTREAM_MAX_PRECISION);
    _precision = v.getPrecision();
    return *this;
}

asciistream &
asciistream::operator >> (Precision v) {
    assert(v.getPrecision() <= VESPALIB_ASCIISTREAM_MAX_PRECISION);
    _precision = v.getPrecision();
    return *this;
}

asciistream::asciistream() :
    _rPos(0),
    _wbuf(),
    _rbuf(_wbuf.c_str(), _wbuf.size()),
    _base(dec),
    _floatSpec(automatic),
    _floatModifier(defaultdotting),
    _width(0),
    _fill(' '),
    _precision(6)
{ }

asciistream::asciistream(stringref buf) :
    _rPos(0),
    _wbuf(),
    _rbuf(buf),
    _base(dec),
    _floatSpec(automatic),
    _floatModifier(defaultdotting),
    _width(0),
    _fill(' '),
    _precision(6)
{
    if (buf[buf.size()] != '\0') {
        _wbuf = buf;
        _rbuf = _wbuf;
    }
}

asciistream::~asciistream() = default;

asciistream::asciistream(const asciistream & rhs) :
    _rPos(0),
    _wbuf(rhs.str()),
    _rbuf(_wbuf.c_str(), _wbuf.size()),
    _base(rhs._base),
    _floatSpec(rhs._floatSpec),
    _floatModifier(rhs._floatModifier),
    _width(rhs._width),
    _fill(rhs._fill),
    _precision(rhs._precision)
{
}

asciistream & asciistream::operator = (const asciistream & rhs)
{
    if (this != &rhs) {
        asciistream newStream(rhs);
        swap(newStream);
    }
    return *this;
}

asciistream::asciistream(asciistream && rhs) noexcept
    : asciistream()
{
    swap(rhs);
}

asciistream & asciistream::operator = (asciistream && rhs) noexcept
{
    if (this != &rhs) {
        swap(rhs);
    }
    return *this;
}

void asciistream::swap(asciistream & rhs) noexcept
{
    std::swap(_rPos, rhs._rPos);
    // If read-only, _wbuf is empty and _rbuf is set
    // If ever written to, _rbuf == _wbuf
    const bool lhs_read_only = (_rbuf.data() != _wbuf.data());
    const bool rhs_read_only = (rhs._rbuf.data() != rhs._wbuf.data());
    std::swap(_wbuf, rhs._wbuf);
    std::swap(_rbuf, rhs._rbuf);
    if (!lhs_read_only) {
        rhs._rbuf = rhs._wbuf;
    }
    if (!rhs_read_only) {
        _rbuf = _wbuf;
    }

    std::swap(_base, rhs._base);
    std::swap(_floatSpec, rhs._floatSpec);
    std::swap(_floatModifier, rhs._floatModifier);
    std::swap(_width, rhs._width);
    std::swap(_precision, rhs._precision);
    std::swap(_fill, rhs._fill);
}

namespace {

int getValue(double & val, const char *buf) __attribute__((noinline));
int getValue(float & val, const char *buf) __attribute__((noinline));
void throwInputError(int e, const char * t, const char * buf) __attribute__((noinline));
void throwInputError(std::errc e, const char * t, const char * buf) __attribute__((noinline));
void throwUnderflow(size_t pos) __attribute__((noinline));
template <typename T>
T strToInt(T & v, const char *begin, const char *end) __attribute__((noinline));

void throwInputError(int e, const char * t, const char * buf)
{
    if (e == 0) {
        throw IllegalArgumentException("Failed decoding a " + string(t) + " from '" + string(buf) + "'.", VESPA_STRLOC);
    } else if (errno == ERANGE) {
        throw IllegalArgumentException(string(t) + " value '" + string(buf) + "' is outside of range.", VESPA_STRLOC);
    } else if (errno == EINVAL) {
        throw IllegalArgumentException("Illegal " + string(t) + " value '" + string(buf) + "'.", VESPA_STRLOC);
    } else {
        throw IllegalArgumentException("Unknown error decoding an " + string(t) + " from '" + string(buf) + "'.", VESPA_STRLOC);
    }
}

void throwInputError(std::errc e, const char * t, const char * buf) {
    if (e == std::errc::invalid_argument) {
        throw IllegalArgumentException("Illegal " + string(t) + " value '" + string(buf) + "'.", VESPA_STRLOC);
    } else if (e == std::errc::result_out_of_range) {
        throw IllegalArgumentException(string(t) + " value '" + string(buf) + "' is outside of range.", VESPA_STRLOC);
    } else {
        throw IllegalArgumentException("Unknown error decoding an " + string(t) + " from '" + string(buf) + "'.", VESPA_STRLOC);
    }
}

void throwUnderflow(size_t pos)
{
    throw IllegalArgumentException(make_string("buffer underflow at pos %ld.", pos), VESPA_STRLOC);
}

int getValue(double & val, const char *buf)
{
    char *ebuf;
    errno = 0;
    val = locale::c::strtod_au(buf, &ebuf);
    if ((errno != 0) || (buf == ebuf)) {
        throwInputError(errno, "double", buf);
    }
    return ebuf - buf;
}

int getValue(float & val, const char *buf)
{
    char *ebuf;
    errno = 0;
    val = locale::c::strtof_au(buf, &ebuf);
    if ((errno != 0) || (buf == ebuf)) {
        throwInputError(errno, "float", buf);
    }
    return ebuf - buf;
}

template <typename T>
T strToInt(T & v, const char *begin, const char *end)
{
    const char * curr = begin;
    for (;(curr < end) && std::isspace(*curr); curr++);

    std::from_chars_result err;
    if (((end - curr) > 2) && (curr[0] == '0') && ((curr[1] | 0x20) == 'x')) {
        err = std::from_chars(curr+2, end, v, 16);
    } else {
        err = std::from_chars(curr, end, v, 10);
    }
    if (err.ec == std::errc::invalid_argument) {
        if (err.ptr >= end) {
            throwUnderflow(err.ptr - begin);
        }
        throwInputError(err.ec, "strToInt", begin);
    } else if (err.ec == std::errc::result_out_of_range) {
        throwInputError(err.ec, "strToInt", begin);
    }

    return err.ptr - begin;
}

}

asciistream & asciistream::operator >> (bool & v)
{
    for (;(_rPos < length()) && std::isspace(_rbuf[_rPos]); _rPos++);
    if (_rPos < length()) {
        v = (_rbuf[_rPos++] != '0');
    } else {
        throwUnderflow(_rPos);
    }
    return *this;
}

asciistream & asciistream::operator >> (char & v)
{
    for (;(_rPos < length()) && std::isspace(_rbuf[_rPos]); _rPos++);
    if (_rPos < length()) {
        v = _rbuf[_rPos++];
    } else {
        throwUnderflow(_rPos);
    }
    return *this;
}

asciistream & asciistream::operator >> (signed char & v)
{
    for (;(_rPos < length()) && std::isspace(_rbuf[_rPos]); _rPos++);
    if (_rPos < length()) {
        v = _rbuf[_rPos++];
    } else {
        throwUnderflow(_rPos);
    }
    return *this;
}

asciistream & asciistream::operator >> (unsigned char & v)
{
    for (;(_rPos < length()) && std::isspace(_rbuf[_rPos]); _rPos++);
    if (_rPos < length()) {
        v = _rbuf[_rPos++];
    } else {
        throwUnderflow(_rPos);
    }
    return *this;
}

asciistream & asciistream::operator >> (unsigned short & v)
{
    _rPos += strToInt(v, &_rbuf[_rPos], &_rbuf[length()]);
    return *this;
}

asciistream & asciistream::operator >> (unsigned int & v)
{
    _rPos += strToInt(v, &_rbuf[_rPos], &_rbuf[length()]);
    return *this;
}

asciistream & asciistream::operator >> (unsigned long & v)
{
    _rPos += strToInt(v, &_rbuf[_rPos], &_rbuf[length()]);
    return *this;
}

asciistream & asciistream::operator >> (unsigned long long & v)
{
    _rPos += strToInt(v, &_rbuf[_rPos], &_rbuf[length()]);
    return *this;
}

asciistream & asciistream::operator >> (short & v)
{
    _rPos += strToInt(v, &_rbuf[_rPos], &_rbuf[length()]);
    return *this;
}

asciistream & asciistream::operator >> (int & v)
{
    _rPos += strToInt(v, &_rbuf[_rPos], &_rbuf[length()]);
    return *this;
}

asciistream & asciistream::operator >> (long & v)
{
    _rPos += strToInt(v, &_rbuf[_rPos], &_rbuf[length()]);
    return *this;
}

asciistream & asciistream::operator >> (long long & v)
{
    _rPos += strToInt(v, &_rbuf[_rPos], &_rbuf[length()]);
    return *this;
}

asciistream & asciistream::operator >> (double & v)
{
    double l(0);
    _rPos += getValue(l, &_rbuf[_rPos]);
    v = l;
    return *this;
}

asciistream & asciistream::operator >> (float & v)
{
    float l(0);
    _rPos += getValue(l, &_rbuf[_rPos]);
    v = l;
    return *this;
}

void asciistream::eatWhite()
{
    for (;(_rPos < length()) && isspace(_rbuf[_rPos]); _rPos++);
}

void asciistream::eatNonWhite()
{
    for (;(_rPos < length()) && !isspace(_rbuf[_rPos]); _rPos++);
}

asciistream & asciistream::operator >> (std::string & v)
{
    eatWhite();
    size_t start(_rPos);
    eatNonWhite();
    v.assign(&_rbuf[start], _rPos-start);
    return *this;
}

asciistream & asciistream::operator >> (string & v)
{
    eatWhite();
    size_t start(_rPos);
    eatNonWhite();
    v.assign(&_rbuf[start], _rPos-start);
    return *this;
}


namespace {
const char * _C_char = "0123456789abcdefg";

char * prependInt(char * tmp, Base base)
{
    if (base == bin) {
        tmp[1] = 'b';
        tmp[0] = '0';
        return tmp;
    }
    return tmp + 2;
}

char * prependSign(bool sign, char * tmp)
{
    if (sign) {
        tmp[0] = '-';
        return tmp;
    }
    return tmp + 1;
}

template <uint8_t base>
uint8_t printInt(unsigned long long r, char * tmp, uint8_t i) __attribute__((noinline));

template <uint8_t base>
uint8_t printInt(unsigned long long r, char * tmp, uint8_t i)
{
    for(; r; i--, r/=base) {
        uint8_t d = r%base;
        tmp[i-1] = (base <= 10) ? d + '0' : _C_char[d];
    }
    return i;
}

}


asciistream & asciistream::operator << (long long v)
{
    char tmp[72];
    uint8_t i(sizeof(tmp));
    bool negative(false);
    if (v == 0) {
        tmp[--i] = '0';
    } else {
        if (v < 0) {
            v = -v;
            negative = true;
        }
        switch (_base) {
          case 2:
            i = printInt<2>(v, tmp, i); break;
          case 8:
            i = printInt<8>(v, tmp, i); break;
          case 10:
            i = printInt<10>(v, tmp, i); break;
          case 16:
            i = printInt<16>(v, tmp, i); break;
          default:
            assert(!"unhandled number base");
        }
    }
    const char *final = prependSign(negative, prependInt(tmp+i-2, _base)-1);
    doFill(sizeof(tmp)-(final-tmp));
    write(final, sizeof(tmp)-(final-tmp));
    return *this;
}

void asciistream::doReallyFill(size_t currWidth)
{
    for (; _width > currWidth; currWidth++) {
        write(&_fill, 1);
    }
}

asciistream & asciistream::operator << (unsigned long long v)
{
    char tmp[72];
    uint8_t i(sizeof(tmp));
    if (v == 0) {
        tmp[--i] = '0';
    } else {
        switch (_base) {
          case 2:
            i = printInt<2>(v, tmp, i); break;
          case 8:
            i = printInt<8>(v, tmp, i); break;
          case 10:
            i = printInt<10>(v, tmp, i); break;
          case 16:
            i = printInt<16>(v, tmp, i); break;
          default:
            assert(!"unhandled number base");
        }
    }
    const char *final = prependInt(tmp+i-2, _base);
    doFill(sizeof(tmp)-(final-tmp));
    write(final, sizeof(tmp)-(final-tmp));
    return *this;
}

namespace {
struct BaseStateSaver {
    asciistream& _stream;
    Base _savedBase;
    BaseStateSaver(asciistream& stream, Base base)
        : _stream(stream), _savedBase(base) {}
    ~BaseStateSaver() {
        _stream << Base(_savedBase);
    }
};
}

asciistream& asciistream::operator<<(const void* p)
{
    BaseStateSaver saver(*this, _base);
    return *this << "0x" << hex << reinterpret_cast<uint64_t>(p);
}

asciistream & asciistream::operator << (float v)
{
    if (_floatSpec == fixed) {
        printFixed(v);
    } else {
        printScientific(v);
    }
    return *this;
}

asciistream & asciistream::operator << (double v)
{
    if (_floatSpec == fixed) {
        printFixed(v);
    } else {
        printScientific(v);
    }
    return *this;
}

template <typename T>
void asciistream::printFixed(T v)
{
    char tmp[sizeof(T)*64]; // Double::max printed fixed takes 316 bytes with default
                  // precision, a high precision adds even more.
    const char *spec = fixedPrecisions[_precision].c_str();
    int len = snprintf(tmp, sizeof(tmp), spec, v);
    assert(len < static_cast<int>(sizeof(tmp)));
    doFill(len);
    write(tmp, len);
}

namespace {
    bool hasDotOrIsScientific(const char* string, size_t len) {
        for (size_t i=0; i<len; ++i) {
            switch (string[i]) {
                case '.':
                case ',':
                case 'e':
                case 'E':
                    return true;
                default:
                    break;
            }
        }
        return false;
    }
}

template <typename T>
void asciistream::printScientific(T v)
{
    char tmp[sizeof(T)*8];
    const char *spec = (((_floatSpec == scientific)
                ? scientificPrecisions[_precision]
                : autoPrecisions[_precision])).c_str();
    int len = snprintf(tmp, sizeof(tmp), spec, v);
    assert(len < static_cast<int>(sizeof(tmp)));
    doFill(len);
    write(tmp, len);
    if (_floatModifier == forcedot && !hasDotOrIsScientific(tmp, len)) {
        write(".0", 2);
    }
}

void asciistream::write(const void * buf, size_t len)
{
    if (_rPos > 0 && _rPos == length()) {
        clear();
    }
    if (_rbuf.data() != _wbuf.data()) {
        if (_wbuf.empty()) {
            _wbuf = _rbuf; // Read only to RW
        } else {
            LOG_ABORT("should not be reached");  // Impossible
        }
    }
    _wbuf.append(buf, len);
    _rbuf = _wbuf;
}

std::vector<string> asciistream::getlines(char delim)
{
    std::vector<string> lines;
    while (!eof()) {
        lines.push_back(getline(delim));
    }
    return lines;
}

string asciistream::getline(char delim)
{
    string line;
    const size_t start(_rPos);
    const size_t end(_rbuf.size());
    for (; (_rPos < end) && (_rbuf[_rPos] != delim); _rPos++);
    if (_rPos > start) {
        line.assign(&_rbuf[start], _rPos - start);
    }
    if (_rPos < end) {
        _rPos++;  // eat the terminating\n
    }
    return line;
}

asciistream asciistream::createFromFile(stringref fileName)
{
    FastOS_File file(vespalib::string(fileName).c_str());
    asciistream is;
    if (file.OpenReadOnly()) {
        ssize_t sz = file.getSize();
        if (sz < 0) {
            throw IoException("Failed getting size of  file " + fileName + " : Error=" + file.getLastErrorString(), IoException::UNSPECIFIED, VESPA_STRLOC);
        }
        MallocPtr buf(sz);
        ssize_t actual = file.Read(buf, sz);
        if (actual != sz) {
            asciistream e;
            e << "Failed reading " << sz << " bytes from file " << fileName;
            throw IoException(e.str() + " : Error=" + file.getLastErrorString(), IoException::UNSPECIFIED, VESPA_STRLOC);
        }
        is << stringref(buf.c_str(), buf.size());
    }
    return is;
}

asciistream asciistream::createFromDevice(stringref fileName)
{
    FastOS_File file(vespalib::string(fileName).c_str());
    asciistream is;
    if (file.OpenReadOnly()) {
        char buf[8_Ki];
        for (ssize_t actual = file.Read(buf, sizeof(buf)); actual > 0; actual = file.Read(buf, sizeof(buf))) {
            is << stringref(buf, actual);
        }
    }
    return is;
}

ssize_t getline(asciistream & is, string & line, char delim)
{
    line = is.getline(delim);
    return line.size();
}


}
