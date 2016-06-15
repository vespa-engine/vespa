// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 1998-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#pragma once

#include <vespa/searchlib/util/rawbuf.h>
#include <vespa/searchsummary/docsummary/resultclass.h>

namespace search {
namespace docsummary {

class DocsumFormat
{
public:
    static size_t addByte(search::RawBuf &target, uint8_t value);
    static size_t addShort(search::RawBuf &target, uint16_t value);
    static size_t addInt32(search::RawBuf &target, uint32_t value);
    static size_t addFloat(search::RawBuf &target, float value);
    static size_t addDouble(search::RawBuf &target, double value);
    static size_t addInt64(search::RawBuf &target, uint64_t value);
    static size_t addShortData(search::RawBuf &target, const char *buf, uint32_t buflen);
    static size_t addLongData(search::RawBuf &target, const char *buf, uint32_t buflen);

    static size_t addEmpty(ResType type, search::RawBuf &target);

    class Appender {
    private:
        search::RawBuf &_target;
    public:
        Appender(search::RawBuf &target) : _target(target) {}

        size_t addByte(uint8_t value) {
            return DocsumFormat::addByte(_target, value);
        }
        size_t addShort(uint16_t value) {
            return DocsumFormat::addShort(_target, value);
        }
        size_t addInt32(uint32_t value) {
            return DocsumFormat::addInt32(_target, value);
        }
        size_t addFloat(float value) {
            return DocsumFormat::addFloat(_target, value);
        }
        size_t addDouble(double value) {
            return DocsumFormat::addDouble(_target, value);
        }
        size_t addInt64(uint64_t value) {
            return DocsumFormat::addInt64(_target, value);
        }
        size_t addShortData(const char *buf, uint32_t buflen) {
            return DocsumFormat::addShortData(_target, buf, buflen);
        }
        size_t addLongData(const char *buf, uint32_t buflen) {
            return DocsumFormat::addLongData(_target, buf, buflen);
        }
         
        size_t addEmpty(ResType type) {
            return DocsumFormat::addEmpty(type, _target);
        }
    };

};

}
}

