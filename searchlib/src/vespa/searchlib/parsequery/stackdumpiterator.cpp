// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "stackdumpiterator.h"
#include <vespa/vespalib/util/compress.h>
#include <vespa/vespalib/objects/nbo.h>
#include <cassert>

using search::query::PredicateQueryTerm;

namespace search {

SimpleQueryStackDumpIterator::SimpleQueryStackDumpIterator(const vespalib::stringref &buf) :
    _buf(buf.begin()),
    _bufEnd(buf.end()),
    _bufLen(buf.size()),
    _currPos(_buf),
    _currEnd(_buf),
    _currType(ParseItem::ITEM_UNDEF),
    _currCreator(ParseItem::CREA_ORIG),
    _currWeight(100),
    _currUniqueId(0),
    _currFlags(0),
    _currArity(0),
    _currArg1(0),
    _currArg2(0),
    _currArg3(0),
    _predicate_query_term(),
    _currIndexName(NULL),
    _currIndexNameLen(0),
    _currTerm(NULL),
    _currTermLen(0),
    _generatedTerm(),
    _currNum(-1)
{
}

SimpleQueryStackDumpIterator::~SimpleQueryStackDumpIterator()
{
}

vespalib::string SimpleQueryStackDumpIterator::readString(const char *&p) {
    if (p >= _bufEnd) throw false;
    uint64_t tmp;
    p += vespalib::compress::Integer::decompressPositive(tmp, p);
    vespalib::string s(p, tmp);
    p += s.size();
    return s;
}

uint64_t SimpleQueryStackDumpIterator::readUint64(const char *&p) {
    if (p + sizeof(uint64_t) > _bufEnd) throw false;
    uint64_t l = vespalib::nbo::n2h(*(const uint64_t *)p);
    p += sizeof(uint64_t);
    return l;
}

uint64_t
SimpleQueryStackDumpIterator::readCompressedPositiveInt(const char *&p) {
    if (p >= _bufEnd) throw false;
    uint64_t tmp;
    p += vespalib::compress::Integer::decompressPositive(tmp, p);
    return tmp;
}

bool
SimpleQueryStackDumpIterator::next()
{
    if (_currEnd >= _bufEnd)
        // End of buffer, so no more items available
        return false;

    // Set the position to the previous end. If just starting, sets pos to _buf
    _currPos = _currEnd;

    // Find an item at the current position
    const char *p = _currPos;
    uint8_t typefield = *p++;
    _currType = ParseItem::GetType(typefield);

    uint64_t tmp(0);
    if (ParseItem::GetFeature_Weight(typefield)) {
        long tmpLong;
        if (p >= _bufEnd) return false;
        p += vespalib::compress::Integer::decompress(tmpLong, p);
        _currWeight.setPercent(tmpLong);
        if (p > _bufEnd) return false;
    } else {
        _currWeight.setPercent(100);
    }
    if (ParseItem::getFeature_UniqueId(typefield)) {
        if (p >= _bufEnd) return false;
        p += vespalib::compress::Integer::decompressPositive(tmp, p);
        _currUniqueId = tmp;
    } else {
        _currUniqueId = 0;
    }
    if (ParseItem::getFeature_Flags(typefield)) {
        if ((p + sizeof(uint32_t)) > _bufEnd) {
            return false;
        }
        _currFlags = (uint8_t)*p++;
    } else {
        _currFlags = 0;
    }
    _currCreator = ParseItem::GetCreator(_currFlags);

    switch (_currType) {
    case ParseItem::ITEM_OR:
    case ParseItem::ITEM_EQUIV:
    case ParseItem::ITEM_AND:
    case ParseItem::ITEM_NOT:
    case ParseItem::ITEM_RANK:
    case ParseItem::ITEM_ANY:
        if (p >= _bufEnd) return false;
        p += vespalib::compress::Integer::decompressPositive(tmp, p);
        _currArity = tmp;
        if (p > _bufEnd) return false;
        _currArg1 = 0;
        _currIndexName = NULL;
        _currIndexNameLen = 0;
        _currTerm = NULL;
        _currTermLen = 0;
        break;

    case ParseItem::ITEM_NEAR:
    case ParseItem::ITEM_ONEAR:
        if (p >= _bufEnd) return false;
        p += vespalib::compress::Integer::decompressPositive(tmp, p);
        _currArity = tmp;
        if (p > _bufEnd) return false;
        p += vespalib::compress::Integer::decompressPositive(tmp, p);
        _currArg1 = tmp;
        if (p > _bufEnd) return false;
        _currIndexName = NULL;
        _currIndexNameLen = 0;
        _currTerm = NULL;
        _currTermLen = 0;
        break;

    case ParseItem::ITEM_WEAK_AND:
        if (p >= _bufEnd) return false;
        p += vespalib::compress::Integer::decompressPositive(tmp, p);
        _currArity = tmp;
        if (p > _bufEnd) return false;
        p += vespalib::compress::Integer::decompressPositive(tmp, p);
        _currArg1 = tmp;
        if (p > _bufEnd) return false;
        p += vespalib::compress::Integer::decompressPositive(tmp, p);
        _currIndexNameLen = tmp;
        if (p > _bufEnd) return false;
        _currIndexName = p;
        p += _currIndexNameLen;
        if (p > _bufEnd) return false;
        _currTerm = NULL;
        _currTermLen = 0;
        break;

    case ParseItem::ITEM_PURE_WEIGHTED_STRING:
        if (p >= _bufEnd) return false;
        p += vespalib::compress::Integer::decompressPositive(tmp, p);
        _currTermLen = tmp;
        if (p > _bufEnd) return false;
        _currTerm = p;
        p += _currTermLen;
        if (p > _bufEnd) return false;

        _currArg1 = 0;
        _currArity = 0;
        break;
    case ParseItem::ITEM_PURE_WEIGHTED_LONG:
        if (p + 8 > _bufEnd) return false;
        _generatedTerm.clear();
        _generatedTerm << vespalib::nbo::n2h(*(const uint64_t *)p);
        _currTerm = _generatedTerm.c_str();
        _currTermLen = _generatedTerm.size();
        p += 8;
        if (p > _bufEnd) return false;

        _currArg1 = 0;
        _currArity = 0;
        break;
    case ParseItem::ITEM_WORD_ALTERNATIVES:
        try {
            _currIndexNameLen = readCompressedPositiveInt(p);
            _currIndexName = p;
            p += _currIndexNameLen;
            _currArity = readCompressedPositiveInt(p);
            _currTerm = NULL;
            _currTermLen = 0;
            if (p > _bufEnd) return false;
        } catch (...) {
            return false;
        }
        break;
    case ParseItem::ITEM_NUMTERM:
    case ParseItem::ITEM_TERM:
    case ParseItem::ITEM_PREFIXTERM:
    case ParseItem::ITEM_SUBSTRINGTERM:
    case ParseItem::ITEM_EXACTSTRINGTERM:
    case ParseItem::ITEM_SUFFIXTERM:
    case ParseItem::ITEM_REGEXP:
        p += vespalib::compress::Integer::decompressPositive(tmp, p);
        _currIndexNameLen = tmp;
        if (p > _bufEnd) return false;
        _currIndexName = p;
        p += _currIndexNameLen;
        if (p > _bufEnd) return false;
        p += vespalib::compress::Integer::decompressPositive(tmp, p);
        _currTermLen = tmp;
        if (p > _bufEnd) return false;
        _currTerm = p;
        p += _currTermLen;
        if (p > _bufEnd) return false;

        _currArg1 = 0;
        _currArity = 0;
        break;
    case ParseItem::ITEM_PREDICATE_QUERY:
        try {
            if (p >= _bufEnd) return false;
            p += vespalib::compress::Integer::decompressPositive(tmp, p);
            _currIndexNameLen = tmp;
            if (p > _bufEnd) return false;
            _currIndexName = p;
            p += _currIndexNameLen;
            _predicate_query_term.reset(new PredicateQueryTerm);

            size_t count = readCompressedPositiveInt(p);
            for (size_t i = 0; i < count; ++i) {
                vespalib::string key = readString(p);
                vespalib::string value = readString(p);
                uint64_t sub_queries = readUint64(p);
                _predicate_query_term->addFeature(key, value, sub_queries);
            }
            count = readCompressedPositiveInt(p);
            for (size_t i = 0; i < count; ++i) {
                vespalib::string key = readString(p);
                uint64_t value = readUint64(p);
                uint64_t sub_queries = readUint64(p);
                _predicate_query_term->addRangeFeature(
                        key, value, sub_queries);
            }
            if (p > _bufEnd) return false;
        } catch (...) {
            return false;
        }
        break;

    case ParseItem::ITEM_WEIGHTED_SET:
    case ParseItem::ITEM_DOT_PRODUCT:
    case ParseItem::ITEM_WAND:
    case ParseItem::ITEM_PHRASE:
    case ParseItem::ITEM_SAME_ELEMENT:
        p += vespalib::compress::Integer::decompressPositive(tmp, p);
        _currArity = tmp;
        if (p > _bufEnd) return false;
        p += vespalib::compress::Integer::decompressPositive(tmp, p);
        _currIndexNameLen = tmp;
        if (p > _bufEnd) return false;
        _currIndexName = p;
        p += _currIndexNameLen;
        if (p > _bufEnd) return false;
        if (_currType == ParseItem::ITEM_WAND) {
            p += vespalib::compress::Integer::decompressPositive(tmp, p); // targetNumHits
            _currArg1 = tmp;
            _currArg2 = vespalib::nbo::n2h(*reinterpret_cast<const double *>(p)); // scoreThreshold
            p += sizeof(double);
            _currArg3 = vespalib::nbo::n2h(*reinterpret_cast<const double *>(p)); // thresholdBoostFactor
            p += sizeof(double);
        } else {
            _currArg1 = 0;
        }
        _currTerm = NULL;
        _currTermLen = 0;
        break;

    default:
        // Unknown item, so report that no more are available
        return false;
        break;
    }
    _currNum++;
    _currEnd = p;

    // We should not have passed the buffer
    assert(_currEnd <= _bufEnd);

    return true;
}

}
