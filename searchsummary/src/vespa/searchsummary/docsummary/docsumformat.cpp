// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docsumformat.h"
#include <cassert>

namespace search::docsummary {

size_t
DocsumFormat::addByte(search::RawBuf &target, uint8_t value)

{
    target.append(&value, sizeof(value));
    return sizeof(value);
}

size_t
DocsumFormat::addShort(search::RawBuf &target, uint16_t value)
{
    target.append(&value, sizeof(value));
    return sizeof(value);
}

size_t
DocsumFormat::addInt32(search::RawBuf &target, uint32_t value)
{
    target.append(&value, sizeof(value));
    return sizeof(value);
}

size_t
DocsumFormat::addFloat(search::RawBuf &target, float value)
{
    target.append(&value, sizeof(value));
    return sizeof(value);
}

size_t
DocsumFormat::addDouble(search::RawBuf &target, double value)
{
    target.append(&value, sizeof(value));
    return sizeof(value);
}

size_t
DocsumFormat::addInt64(search::RawBuf &target, uint64_t value)
{
    target.append(&value, sizeof(value));
    return sizeof(value);
}

size_t
DocsumFormat::addShortData(search::RawBuf &target, const char *buf, uint32_t buflen)
{
    uint16_t len = (buflen > 0xffff ? 0xffff : buflen);
    target.append(&len, sizeof(len));
    target.append(buf, len);

    return sizeof(len) + len;
}

size_t
DocsumFormat::addLongData(search::RawBuf &target, const char *buf, uint32_t buflen)
{
    target.append(&buflen, sizeof(buflen));
    target.append(buf, buflen);

    return sizeof(buflen) + buflen;
}

size_t
DocsumFormat::addEmpty(ResType type, search::RawBuf &target)
{
    switch (type) {
    case RES_BYTE:
        return addByte(target, 0);
    case RES_SHORT:
        return addShort(target, 0);
    case RES_INT:
        return addInt32(target, 0);
    case RES_INT64:
        return addInt64(target, 0L);
    case RES_FLOAT:
        return addFloat(target, 0.0f);
    case RES_DOUBLE:
        return addDouble(target, 0.0);
    case RES_STRING:
    case RES_DATA:
        return addShortData(target, "", 0);
    case RES_LONG_STRING:
    case RES_LONG_DATA:
    case RES_XMLSTRING:
    case RES_JSONSTRING:
    case RES_TENSOR:
    case RES_FEATUREDATA:
        return addLongData(target, "", 0);
    }
    assert(type <= RES_FEATUREDATA);
    return 0;
}

}
