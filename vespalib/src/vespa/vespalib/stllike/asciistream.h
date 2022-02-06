// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace vespalib {

///Modifiers to make usage similar to std::stream
enum Base {bin=2, oct=8, dec=10, hex=16};

///Modifiers to make usage similar to std::stream
enum FloatSpec {automatic, fixed, scientific};

enum FloatModifier {defaultdotting, forcedot};

#define VESPALIB_ASCIISTREAM_MAX_PRECISION 32

/**
 * This is a simple stream intended for building strings the stream
 * way.  Everything is streamed into an underlying memory buffer that
 * can be retrieved as a string. It is supposed to be compliant to the
 * std::stringstream API.  The stream has both a read and a write
 * pointer, so it can be used for both input and output.
 *
 * However it is considerably more lightweight than the std::stream
 * variant. It does not support 'locale' and other expensive stuff;
 * and can be seen as "convenience without sacrificing speed".
*/
class asciistream
{
public:
    asciistream();
    asciistream(stringref buf);
    ~asciistream();
    asciistream(const asciistream & rhs);
    asciistream & operator = (const asciistream & rhs);
    asciistream(asciistream &&) noexcept;
    asciistream & operator = (asciistream &&) noexcept;
    void swap(asciistream & rhs) noexcept;
    asciistream & operator << (bool v)                { if (v) { *this << '1'; } else { *this << '0'; } return *this; }
    asciistream & operator << (char v)                { doFill(1); write(&v, 1); return *this; }
    asciistream & operator << (signed char v)         { doFill(1); write(&v, 1); return *this; }
    asciistream & operator << (unsigned char v)       { doFill(1); write(&v, 1); return *this; }
    asciistream & operator << (const char * v)        { if (v != nullptr) { size_t n(strlen(v)); doFill(n); write(v, n); } return *this; }
    asciistream & operator << (const string & v)      { doFill(v.size()); write(v.data(), v.size()); return *this; }
    asciistream & operator << (stringref v)           { doFill(v.size()); write(v.data(), v.size()); return *this; }
    asciistream & operator << (const std::string & v) { doFill(v.size()); write(v.data(), v.size()); return *this; }
    asciistream & operator << (short v)    { return *this << static_cast<long long>(v); }
    asciistream & operator << (unsigned short v)   { return *this << static_cast<unsigned long long>(v); }
    asciistream & operator << (int v)    { return *this << static_cast<long long>(v); }
    asciistream & operator << (unsigned int v)   { return *this << static_cast<unsigned long long>(v); }
    asciistream & operator << (const void* p);
    asciistream & operator << (long v)      { return *this << static_cast<long long>(v); }
    asciistream & operator << (unsigned long v) { return *this << static_cast<unsigned long long>(v); }
    asciistream & operator << (long long v);
    asciistream & operator << (unsigned long long v);
    asciistream & operator << (float v);
    asciistream & operator << (double v);
    asciistream & operator << (Base v)                { _base = v; return *this; }
    asciistream & operator >> (Base v)                { _base = v; return *this; }
    asciistream & operator << (FloatSpec v)           { _floatSpec = v; return *this; }
    asciistream & operator >> (FloatSpec v)           { _floatSpec = v; return *this; }
    asciistream & operator << (FloatModifier v)           { _floatModifier = v; return *this; }
    asciistream & operator >> (FloatModifier v)           { _floatModifier = v; return *this; }
    asciistream & operator >> (bool & v);
    asciistream & operator >> (char & v);
    asciistream & operator >> (signed char & v);
    asciistream & operator >> (unsigned char & v);
    asciistream & operator >> (std::string & v);
    asciistream & operator >> (string & v);
    asciistream & operator >> (short & v);
    asciistream & operator >> (unsigned short & v);
    asciistream & operator >> (int & v);
    asciistream & operator >> (unsigned int & v);
    asciistream & operator >> (long & v);
    asciistream & operator >> (unsigned long & v);
    asciistream & operator >> (long long & v);
    asciistream & operator >> (unsigned long long & v);
    asciistream & operator >> (float & v);
    asciistream & operator >> (double & v);
    stringref str() const { return stringref(c_str(), size()); }
    const char * c_str() const { return _rbuf.data() + _rPos; }
    size_t        size() const { return length() - _rPos; }
    bool         empty() const { return size() == 0; }
    bool           eof() const { return empty(); }
    bool          fail() const { return false; }
    size_t    capacity() const { return _wbuf.capacity(); }
    void         clear() { _rPos = 0; _wbuf.clear(); _rbuf = _wbuf; }
    class Width {
    public:
        Width(size_t width) : _width(width) { }
        size_t getWidth() const { return _width; }
    private:
        uint32_t _width;
    };
    class Fill {
    public:
        Fill(char fill) : _fill(fill) { }
        char getFill() const { return _fill; }
    private:
        char _fill;
    };
    class Precision {
    public:
        Precision(size_t precision) : _precision(precision) { }
        size_t getPrecision() const { return _precision; }
    private:
        uint32_t _precision;
    };
    class StateSaver {
    public:
        // Don't generate move/copy constructors as this class is only intended to
        // live on a single stack frame
        StateSaver(StateSaver&&) = delete;
        StateSaver& operator=(StateSaver&&) = delete;
        StateSaver(const StateSaver&) = delete;
        StateSaver& operator=(const StateSaver&) = delete;

        explicit StateSaver(asciistream& as) noexcept :
                _as(as),
                _base(as._base),
                _floatSpec(as._floatSpec),
                _floatModifier(as._floatModifier),
                _width(as._width),
                _fill(as._fill),
                _precision(as._precision) {}
        ~StateSaver() noexcept {
            _as._base = _base;
            _as._floatSpec = _floatSpec;
            _as._floatModifier = _floatModifier;
            _as._width = _width;
            _as._fill = _fill;
            _as._precision = _precision;
        }
    private:
        asciistream&    _as;
        Base            _base;
        FloatSpec       _floatSpec;
        FloatModifier   _floatModifier;
        uint32_t        _width;
        char            _fill;
        uint8_t         _precision;
    };

    asciistream & operator << (Width v)      { _width = v.getWidth(); return *this; }
    asciistream & operator >> (Width v)      { _width = v.getWidth(); return *this; }
    asciistream & operator << (Fill v)       { _fill = v.getFill(); return *this; }
    asciistream & operator >> (Fill v)       { _fill = v.getFill(); return *this; }
    asciistream & operator << (Precision v);
    asciistream & operator >> (Precision v);
    void eatWhite();
    static asciistream createFromFile(stringref fileName);
    static asciistream createFromDevice(stringref fileName);
    string getline(char delim='\n');
    char getFill() const noexcept { return _fill; }
    size_t getWidth() const noexcept { return static_cast<size_t>(_width); } // match input type of setw
    Base getBase() const noexcept { return _base; }
private:
    template <typename T>
    void printFixed(T v) __attribute__((noinline));
    template <typename T>
    void printScientific(T v) __attribute__((noinline));
    void eatNonWhite();
    void doReallyFill(size_t currWidth);
    void doFill(size_t currWidth) {
        if (_width > currWidth) {
            doReallyFill(currWidth);
        }
        _width = 0;
    }
    void write(const void * buf, size_t len);
    size_t length() const { return _rbuf.size(); }
    size_t        _rPos;
    string        _wbuf;
    stringref     _rbuf;
    Base          _base;
    FloatSpec     _floatSpec;
    FloatModifier _floatModifier;
    uint32_t      _width;
    char          _fill;
    uint8_t       _precision;
};

ssize_t getline(asciistream & is, vespalib::string & line, char delim='\n');

inline asciistream::Width setw(size_t v)             { return asciistream::Width(v); }
inline asciistream::Fill setfill(char v)             { return asciistream::Fill(v); }
inline asciistream::Precision setprecision(size_t v) { return asciistream::Precision(v); }

}

