// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "stackdumpiterator.h"
#include <vespa/vespalib/util/compress.h>
#include <vespa/vespalib/objects/nbo.h>
#include <cassert>
#include <charconv>

using search::query::PredicateQueryTerm;

namespace search {

SimpleQueryStackDumpIterator::SimpleQueryStackDumpIterator(vespalib::stringref buf)
    : _buf(buf.begin()),
      _bufEnd(buf.end()),
      _currPos(0),
      _currEnd(0),
      _currType(ParseItem::ITEM_UNDEF),
      _currFlags(0),
      _currWeight(100),
      _currUniqueId(0),
      _currArity(0),
      _curr_index_name(),
      _curr_term(),
      _curr_integer_term(0),
      _extraIntArg1(0),
      _extraIntArg2(0),
      _extraIntArg3(0),
      _extraDoubleArg4(0),
      _extraDoubleArg5(0),
      _predicate_query_term()
{
}

SimpleQueryStackDumpIterator::~SimpleQueryStackDumpIterator() = default;

vespalib::stringref
SimpleQueryStackDumpIterator::read_stringref(const char *&p)
{
    uint64_t len;
    p += vespalib::compress::Integer::decompressPositive(len, p);
    if ((p + len) > _bufEnd) throw false;
    vespalib::stringref result(p, len);
    p += len;
    return result;
}

uint64_t
SimpleQueryStackDumpIterator::readUint64(const char *&p)
{
    uint64_t value;
    memcpy(&value, p, sizeof(value));
    p += sizeof(value);
    return vespalib::nbo::n2h(value);
}

double
SimpleQueryStackDumpIterator::read_double(const char *&p)
{
    double value;
    memcpy(&value, p, sizeof(value));
    p += sizeof(value);
    return vespalib::nbo::n2h(value);
}

uint64_t
SimpleQueryStackDumpIterator::readCompressedPositiveInt(const char *&p)
{
    uint64_t tmp;
    p += vespalib::compress::Integer::decompressPositive(tmp, p);
    if (p > _bufEnd) throw false;
    return tmp;
}

bool
SimpleQueryStackDumpIterator::next() {
    try {
        return readNext();
    } catch (...) {
        return false;
    }
}

bool SimpleQueryStackDumpIterator::readNext() {
    if ((_buf + _currEnd) >= _bufEnd)
        // End of buffer, so no more items available
        return false;

    // Set the position to the previous end. If just starting, sets pos to _buf
    _currPos = _currEnd;

    // Find an item at the current position
    const char *p = _buf + _currPos;
    uint8_t typefield = *p++;
    _currType = ParseItem::GetType(typefield);

    if (ParseItem::GetFeature_Weight(typefield)) {
        int64_t tmpLong;
        if (p >= _bufEnd) return false;
        p += vespalib::compress::Integer::decompress(tmpLong, p);
        _currWeight.setPercent(tmpLong);
        if (p > _bufEnd) return false;
    } else {
        _currWeight.setPercent(100);
    }
    if (__builtin_expect(ParseItem::getFeature_UniqueId(typefield), false)) {
        _currUniqueId = readCompressedPositiveInt(p);
    } else {
        _currUniqueId = 0;
    }
    if (__builtin_expect(ParseItem::getFeature_Flags(typefield), false)) {
        if ((p + sizeof(uint8_t)) > _bufEnd) return false;
        _currFlags = (uint8_t)*p++;
    } else {
        _currFlags = 0;
    }

    switch (_currType) {
    case ParseItem::ITEM_OR:
    case ParseItem::ITEM_EQUIV:
    case ParseItem::ITEM_AND:
    case ParseItem::ITEM_NOT:
    case ParseItem::ITEM_RANK:
    case ParseItem::ITEM_ANY:
        _currArity = readCompressedPositiveInt(p);
        _curr_index_name = vespalib::stringref();
        _curr_term = vespalib::stringref();
        break;

    case ParseItem::ITEM_NEAR:
    case ParseItem::ITEM_ONEAR:
        _currArity = readCompressedPositiveInt(p);
        _extraIntArg1 = readCompressedPositiveInt(p);
        _curr_index_name = vespalib::stringref();
        _curr_term = vespalib::stringref();
        break;

    case ParseItem::ITEM_WEAK_AND:
        _currArity = readCompressedPositiveInt(p);
        _extraIntArg1 = readCompressedPositiveInt(p); // targetNumHits
        _curr_index_name = read_stringref(p);
        _curr_term = vespalib::stringref();
        break;
    case ParseItem::ITEM_SAME_ELEMENT:
        _currArity = readCompressedPositiveInt(p);
        _curr_index_name = read_stringref(p);
        _curr_term = vespalib::stringref();
        break;

    case ParseItem::ITEM_PURE_WEIGHTED_STRING:
        _curr_term = read_stringref(p);
        _currArity = 0;
        break;
    case ParseItem::ITEM_PURE_WEIGHTED_LONG:
        {
            if (p + sizeof(int64_t) > _bufEnd) return false;
            _curr_integer_term = vespalib::nbo::n2h(*reinterpret_cast<const int64_t *>(p));
            p += sizeof(int64_t);
            _currArity = 0;
        }
        break;
    case ParseItem::ITEM_WORD_ALTERNATIVES:
        _curr_index_name = read_stringref(p);
        _currArity = readCompressedPositiveInt(p);
        _curr_term = vespalib::stringref();
        break;
    case ParseItem::ITEM_NUMTERM:
    case ParseItem::ITEM_GEO_LOCATION_TERM:
    case ParseItem::ITEM_TERM:
    case ParseItem::ITEM_PREFIXTERM:
    case ParseItem::ITEM_SUBSTRINGTERM:
    case ParseItem::ITEM_EXACTSTRINGTERM:
    case ParseItem::ITEM_SUFFIXTERM:
    case ParseItem::ITEM_REGEXP:
        _curr_index_name = read_stringref(p);
        _curr_term = read_stringref(p);
        _currArity = 0;
        break;
    case ParseItem::ITEM_PREDICATE_QUERY:
        if ( ! readPredicate(p)) return false;
        break;

    case ParseItem::ITEM_WEIGHTED_SET:
    case ParseItem::ITEM_DOT_PRODUCT:
    case ParseItem::ITEM_WAND:
    case ParseItem::ITEM_PHRASE:
        if (!readComplexTerm(p)) return false;
        break;
    case ParseItem::ITEM_NEAREST_NEIGHBOR:
        if ( ! readNN(p)) return false;
        break;
    case ParseItem::ITEM_FUZZY:
        if (!readFuzzy(p)) return false;
        break;
    case ParseItem::ITEM_TRUE:
    case ParseItem::ITEM_FALSE:
        // no content
        break;
    default:
        // Unknown item, so report that no more are available
        return false;
    }
    _currEnd = p - _buf;

    // We should not have passed the buffer
    return (p <= _bufEnd);
}

bool
SimpleQueryStackDumpIterator::readPredicate(const char *&p) {
    _curr_index_name = read_stringref(p);
    _predicate_query_term = std::make_unique<PredicateQueryTerm>();

    size_t count = readCompressedPositiveInt(p);
    for (size_t i = 0; i < count; ++i) {
        vespalib::stringref key = read_stringref(p);
        vespalib::stringref value = read_stringref(p);
        if (p + sizeof(uint64_t) > _bufEnd) return false;
        uint64_t sub_queries = readUint64(p);
        _predicate_query_term->addFeature(key, value, sub_queries);
    }
    count = readCompressedPositiveInt(p);
    for (size_t i = 0; i < count; ++i) {
        vespalib::stringref key = read_stringref(p);
        if (p + 2*sizeof(uint64_t) > _bufEnd) return false;
        uint64_t value = readUint64(p);
        uint64_t sub_queries = readUint64(p);
        _predicate_query_term->addRangeFeature(key, value, sub_queries);
    }
    return true;
}

bool
SimpleQueryStackDumpIterator::readNN(const char *& p) {
    _curr_index_name = read_stringref(p);
    _curr_term = read_stringref(p); // query_tensor_name
    _extraIntArg1 = readCompressedPositiveInt(p); // targetNumHits
    _extraIntArg2 = readCompressedPositiveInt(p); // allow_approximate
    _extraIntArg3 = readCompressedPositiveInt(p); // explore_additional_hits
    // XXX: remove later when QRS doesn't send this extra flag
    _extraIntArg2 &= ~0x40;
    // QRS always sends this now:
    if ((p + sizeof(double))> _bufEnd) return false;
    _extraDoubleArg4 = read_double(p); // distance threshold
    _currArity = 0;
    return true;
}

bool
SimpleQueryStackDumpIterator::readComplexTerm(const char *& p) {
    _currArity = readCompressedPositiveInt(p);
    _curr_index_name = read_stringref(p);
    if (_currType == ParseItem::ITEM_WAND) {
        _extraIntArg1 = readCompressedPositiveInt(p); // targetNumHits
        if ((p + 2*sizeof(double))> _bufEnd) return false;
        _extraDoubleArg4 = read_double(p); // scoreThreshold
        _extraDoubleArg5 = read_double(p); // thresholdBoostFactor
    }
    _curr_term = vespalib::stringref();
    return true;
}

bool
SimpleQueryStackDumpIterator::readFuzzy(const char *&p) {
    _curr_index_name = read_stringref(p);
    _curr_term = read_stringref(p); // fuzzy term
    _extraIntArg1 = readCompressedPositiveInt(p); // maxEditDistance
    _extraIntArg2 = readCompressedPositiveInt(p); // prefixLength
    _currArity = 0;
    return true;
}

}
