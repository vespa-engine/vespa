// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "jsonwriter.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <cmath>
#include <cassert>

namespace vespalib {

void
JSONWriter::push(State next)
{
    _stack.push_back(next);
}

void
JSONWriter::pop(State expected)
{
    State actual = _stack.back();
    assert(actual == expected);
    (void) actual;
    (void) expected;
    _stack.pop_back();
}

void
JSONWriter::considerComma()
{
    if (_comma) {
        (*_os) << ',';
    }
}

void
JSONWriter::updateCommaState()
{
    if (_stack.back() == ARRAY || _stack.back() == OBJECT) {
        _comma = true;
    } else {
        _comma = false;
    }
}

void
JSONWriter::quote(const char * str, size_t len)
{
    std::vector<char> v((len+1)*2 + 1);
    v[0] = '\"';
    size_t j(1);
    for (size_t i = 0; i < len; ++i) {
        switch (str[i]) {
        case '\b': v[j++] = '\\'; v[j++] = 'b'; break;
        case '\f': v[j++] = '\\'; v[j++] = 'f'; break;
        case '\n': v[j++] = '\\'; v[j++] = 'n'; break;
        case '\r': v[j++] = '\\'; v[j++] = 'r'; break;
        case '\t': v[j++] = '\\'; v[j++] = 't'; break;
        case '\"':
        case '\\':
            v[j++] = '\\';
            [[fallthrough]];
        default:
            v[j++] = str[i];
            break;
        }
    }
    v[j++] = '\"';
    v[j++] = 0;
    (*_os) << &v[0];
}

JSONWriter::JSONWriter() :
    _os(NULL),
    _stack(),
    _comma(false),
    _pretty(false),
    _indent(0)
{
    clear();
}

JSONWriter::JSONWriter(vespalib::asciistream & output) :
    _os(&output),
    _stack(),
    _comma(false),
    _pretty(false),
    _indent(0)
{
    clear();
    (*_os) << vespalib::asciistream::Precision(16) << vespalib::forcedot;
}

JSONWriter::~JSONWriter() {}

JSONWriter&
JSONWriter::setOutputStream(vespalib::asciistream & output) {
    _os = &output;
    (*_os) << vespalib::asciistream::Precision(16) << vespalib::forcedot;
    return *this;
}

void
JSONWriter::indent()
{
    if (_pretty) {
        (*_os) << "\n";
        for (uint32_t i = 0; i < _indent; ++i) {
            (*_os) << "  ";
        }
    }
}

JSONWriter &
JSONWriter::clear()
{
    _stack.clear();
    _stack.push_back(INIT);
    _comma = false;
    return *this;
}

JSONWriter &
JSONWriter::beginObject()
{
    push(OBJECT);
    considerComma();
    indent();
    (*_os) << '{';
    _indent++;
    _comma = false;
    return *this;
}

JSONWriter &
JSONWriter::endObject()
{
    pop(OBJECT);
    _indent--;
    indent();
    (*_os) << '}';
    updateCommaState();
    return *this;
}

JSONWriter &
JSONWriter::beginArray()
{
    push(ARRAY);
    considerComma();
    indent();
    (*_os) << '[';
    _indent++;
    _comma = false;
    return *this;
}

JSONWriter &
JSONWriter::endArray()
{
    pop(ARRAY);
    _indent--;
    indent();
    (*_os) << ']';
    updateCommaState();
    return *this;
}

JSONWriter &
JSONWriter::appendNull()
{
    considerComma();
    (*_os) << "null";
    updateCommaState();
    return *this;
}

JSONWriter &
JSONWriter::appendKey(const vespalib::stringref & str)
{
    considerComma();
    indent();
    quote(str.c_str(), str.size());
    (*_os) << ':';
    _comma = false;
    return *this;
}

JSONWriter &
JSONWriter::appendBool(bool v)
{
    considerComma();
    (*_os) << (v ? "true" : "false");
    updateCommaState();
    return *this;
}

JSONWriter &
JSONWriter::appendDouble(double v)
{
    considerComma();
    if (!std::isinf(v) && !std::isnan(v)) {
        // Doubles can represent all whole numbers up to 2^53, which has
        // 16 digits. A precision of 16 should allow thus fit.
        (*_os) << vespalib::asciistream::Precision(16)
               << vespalib::automatic << v;
    } else {
        (*_os) << "null";
    }
    updateCommaState();
    return *this;
}

JSONWriter &
JSONWriter::appendFloat(float v)
{
    considerComma();
    if (!std::isinf(v) && !std::isnan(v)) {
        // Floats can represent all whole numbers up to 2^24, which has
        // 8 digits. A precision of 8 should allow thus fit.
        (*_os) << vespalib::asciistream::Precision(8)
               << vespalib::automatic << v;
    } else {
        (*_os) << "null";
    }
    updateCommaState();
    return *this;
}

JSONWriter &
JSONWriter::appendInt64(int64_t v)
{
    considerComma();
    (*_os) << v;
    updateCommaState();
    return *this;
}

JSONWriter &
JSONWriter::appendUInt64(uint64_t v)
{
    considerComma();
    (*_os) << v;
    updateCommaState();
    return *this;
}

JSONWriter &
JSONWriter::appendString(const vespalib::stringref & str)
{
    considerComma();
    quote(str.c_str(), str.size());
    updateCommaState();
    return *this;
}

JSONWriter &
JSONWriter::appendJSON(const vespalib::stringref & json)
{
    considerComma();
    (*_os) << json;
    updateCommaState();
    return *this;
}

JSONStringer::JSONStringer() :
    JSONWriter(),
    _oss(std::make_unique<asciistream>())
{
    setOutputStream(*_oss);
}

JSONStringer &
JSONStringer::clear()
{
    JSONWriter::clear();
    // clear the string stream as well
    _oss->clear();
    return *this;
}

JSONStringer::~JSONStringer() { }

stringref
JSONStringer::toString() const {
    return _oss->str();
}

}
