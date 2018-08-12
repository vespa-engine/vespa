// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <stdint.h>
#include <vespa/vespalib/stllike/string.h>

namespace vespalib {

template<typename K> struct hash {
    // specializations operate as functor for known key types
    size_t operator() (const K & v) const {
        return v.hash();
    }
};

template<> struct hash<char> {
    size_t operator() (char arg) const { return arg; }
};
template<> struct hash<int8_t> {
    size_t operator() (int8_t arg) const { return arg; }
};
template<> struct hash<int16_t> {
    size_t operator() (int16_t arg) const { return arg; }
};
template<> struct hash<int32_t> {
    size_t operator() (int32_t arg) const { return arg; }
};
template<> struct hash<int64_t> {
    size_t operator() (int64_t arg) const { return arg; }
};

template<> struct hash<uint8_t> {
    size_t operator() (uint8_t arg) const { return arg; }
};
template<> struct hash<uint16_t> {
    size_t operator() (uint16_t arg) const { return arg; }
};
template<> struct hash<uint32_t> {
    size_t operator() (uint32_t arg) const { return arg; }
};
template<> struct hash<uint64_t> {
    size_t operator() (uint64_t arg) const { return arg; }
};

template<> struct hash<float> {
    union U { float f; uint32_t i; };
    size_t operator() (float arg) const { U t; t.f = arg; return t.i; }
};
template<> struct hash<double> {
    union U { double f; uint64_t i; };
    size_t operator() (double arg) const { U t; t.f = arg; return t.i; }
};

template<typename T> struct hash<T *> {
    size_t operator() (const T * arg) const { return size_t(arg); }
};
template<typename T> struct hash<const T *> {
    size_t operator() (const T * arg) const { return size_t(arg); }
};

// reuse old string hash function
extern size_t hashValue(const char *str);
extern size_t hashValue(const void *str, size_t sz);

template<> struct hash<const char *> {
    size_t operator() (const char * arg) const { return hashValue(arg); }
};

template<> struct hash<vespalib::stringref> {
    size_t operator() (vespalib::stringref arg) const { return hashValue(arg.data(), arg.size()); }
};

template<> struct hash<vespalib::string> {
    size_t operator() (const vespalib::string & arg) const { return hashValue(arg.c_str()); }
};

template<> struct hash<std::string> {
    size_t operator() (const std::string& arg) const { return hashValue(arg.c_str()); }
};

template<typename V> struct size {
    size_t operator() (const V & arg) const { return arg.size(); }
};

template<typename V> struct zero {
    size_t operator() (const V & ) const { return 0; }
};


} // namespace vespalib

