// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vector>
#include <vespa/vespalib/stllike/asciistream.h>

namespace vespalib {

/**
 * If you want a simpler interface to write JSON with, look at the JsonStream
 * class in the same directory as this class, this uses this class to make a
 * more user friendly writer.
 */
class JSONWriter {
private:
    enum State {
        INIT = 0,
        OBJECT,
        ARRAY
    };
    vespalib::asciistream * _os;
    std::vector<State>      _stack;
    bool                    _comma;
    bool                    _pretty;
    uint32_t                _indent;

    void push(State next);
    void pop(State expected);
    void considerComma();
    void updateCommaState();
    void quote(const char * str, size_t len);
    void indent();

public:
    JSONWriter();
    JSONWriter(vespalib::asciistream & output);

    JSONWriter & setOutputStream(vespalib::asciistream & output);
    JSONWriter & clear();
    JSONWriter & beginObject();
    JSONWriter & endObject();
    JSONWriter & beginArray();
    JSONWriter & endArray();
    JSONWriter & appendNull();
    JSONWriter & appendKey(const vespalib::stringref & str);
    JSONWriter & appendBool(bool v);
    JSONWriter & appendDouble(double v);
    JSONWriter & appendFloat(float v);
    JSONWriter & appendInt64(int64_t v);
    JSONWriter & appendUInt64(uint64_t v);
    JSONWriter & appendString(const vespalib::stringref & str);
    JSONWriter & appendJSON(const vespalib::stringref & json);

    void setPretty() { _pretty = true; };
};

class JSONStringer : public JSONWriter {
private:
    vespalib::asciistream _oss;

public:
    JSONStringer();
    JSONStringer & clear();
    vespalib::stringref toString() { return _oss.str(); }
};

template<typename T>
struct JSONPrinter
{
    static void printJSON(vespalib::JSONWriter& w, T v) {
        w.appendInt64(v);
    }
};

template<>
struct JSONPrinter<uint64_t>
{
    static void printJSON(vespalib::JSONWriter& w, uint64_t v) {
        w.appendUInt64(v);
    }
};

template<>
struct JSONPrinter<float>
{
    static void printJSON(vespalib::JSONWriter& w, float v) {
        w.appendDouble(v);
    }
};

template<>
struct JSONPrinter<double>
{
    static void printJSON(vespalib::JSONWriter& w, double v) {
        w.appendDouble(v);
    }
};


}

