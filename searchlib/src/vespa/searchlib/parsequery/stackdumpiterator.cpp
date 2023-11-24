// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "stackdumpiterator.h"
#include <vespa/searchlib/query/tree/integer_term_vector.h>
#include <vespa/searchlib/query/tree/predicate_query_term.h>
#include <vespa/searchlib/query/tree/string_term_vector.h>
#include <vespa/vespalib/util/compress.h>
#include <vespa/vespalib/objects/nbo.h>
#include <cassert>

using search::query::IntegerTermVector;
using search::query::PredicateQueryTerm;
using search::query::StringTermVector;

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
      _predicate_query_term(),
      _terms()
{
}

SimpleQueryStackDumpIterator::~SimpleQueryStackDumpIterator() = default;

vespalib::stringref
SimpleQueryStackDumpIterator::read_stringref(const char *&p)
{
    uint64_t len = readCompressedPositiveInt(p);
    if ((p + len) > _bufEnd) throw false;
    vespalib::stringref result(p, len);
    p += len;
    return result;
}

uint64_t
SimpleQueryStackDumpIterator::readCompressedPositiveInt(const char *&p)
{
    if (p > _bufEnd || !vespalib::compress::Integer::check_decompress_space(p, _bufEnd - p)) {
        throw false;
    }
    uint64_t tmp;
    p += vespalib::compress::Integer::decompressPositive(tmp, p);
    assert(p <= _bufEnd);
    return tmp;
}

int64_t
SimpleQueryStackDumpIterator::readCompressedInt(const char *&p)
{
    if (p > _bufEnd || !vespalib::compress::Integer::check_decompress_positive_space(p, _bufEnd - p)) {
        throw false;
    }
    int64_t tmp;
    p += vespalib::compress::Integer::decompress(tmp, p);
    assert(p <= _bufEnd);
    return tmp;
}

template <typename T>
T
SimpleQueryStackDumpIterator::read_value(const char *&p)
{
    T value;
    if (p + sizeof(value) > _bufEnd) {
        throw false;
    }
    memcpy(&value, p, sizeof(value));
    p += sizeof(value);
    return vespalib::nbo::n2h(value);
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
    uint8_t type_code = typefield & ParseItem::item_type_mask;
    if (type_code == ParseItem::item_type_extension_mark) {
        if (p >= _bufEnd || ((uint8_t) *p) >= 0x80) {
            return false;
        }
        type_code += (uint8_t) *p++;
    }
    _currType = static_cast<ParseItem::ItemType>(type_code);

    if (ParseItem::GetFeature_Weight(typefield)) {
        int64_t tmpLong = readCompressedInt(p);
        _currWeight.setPercent(tmpLong);
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
        _curr_integer_term = read_value<int64_t>(p);
        _currArity = 0;
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
        readPredicate(p);
        break;

    case ParseItem::ITEM_WEIGHTED_SET:
    case ParseItem::ITEM_DOT_PRODUCT:
    case ParseItem::ITEM_WAND:
    case ParseItem::ITEM_PHRASE:
        readComplexTerm(p);
        break;
    case ParseItem::ITEM_NEAREST_NEIGHBOR:
        readNN(p);
        break;
    case ParseItem::ITEM_FUZZY:
        readFuzzy(p);
        break;
    case ParseItem::ITEM_TRUE:
    case ParseItem::ITEM_FALSE:
        // no content
        _currArity = 0;
        break;
    case ParseItem::ITEM_STRING_IN:
        read_string_in(p);
        break;
    case ParseItem::ITEM_NUMERIC_IN:
        read_numeric_in(p);
        break;
    default:
        // Unknown item, so report that no more are available
        return false;
    }
    _currEnd = p - _buf;

    // We should not have passed the buffer
    return (p <= _bufEnd);
}

void
SimpleQueryStackDumpIterator::readPredicate(const char *&p) {
    _curr_index_name = read_stringref(p);
    _predicate_query_term = std::make_unique<PredicateQueryTerm>();

    size_t count = readCompressedPositiveInt(p);
    for (size_t i = 0; i < count; ++i) {
        vespalib::stringref key = read_stringref(p);
        vespalib::stringref value = read_stringref(p);
        uint64_t sub_queries = read_value<uint64_t>(p);
        _predicate_query_term->addFeature(key, value, sub_queries);
    }
    count = readCompressedPositiveInt(p);
    for (size_t i = 0; i < count; ++i) {
        vespalib::stringref key = read_stringref(p);
        uint64_t value = read_value<uint64_t>(p);
        uint64_t sub_queries = read_value<uint64_t>(p);
        _predicate_query_term->addRangeFeature(key, value, sub_queries);
    }
}

void
SimpleQueryStackDumpIterator::readNN(const char *& p) {
    _curr_index_name = read_stringref(p);
    _curr_term = read_stringref(p); // query_tensor_name
    _extraIntArg1 = readCompressedPositiveInt(p); // targetNumHits
    _extraIntArg2 = readCompressedPositiveInt(p); // allow_approximate
    _extraIntArg3 = readCompressedPositiveInt(p); // explore_additional_hits
    // XXX: remove later when QRS doesn't send this extra flag
    _extraIntArg2 &= ~0x40;
    // QRS always sends this now:
    _extraDoubleArg4 = read_value<double>(p); // distance threshold
    _currArity = 0;
}

void
SimpleQueryStackDumpIterator::readComplexTerm(const char *& p) {
    _currArity = readCompressedPositiveInt(p);
    _curr_index_name = read_stringref(p);
    if (_currType == ParseItem::ITEM_WAND) {
        _extraIntArg1 = readCompressedPositiveInt(p); // targetNumHits
        _extraDoubleArg4 = read_value<double>(p); // scoreThreshold
        _extraDoubleArg5 = read_value<double>(p); // thresholdBoostFactor
    }
    _curr_term = vespalib::stringref();
}

void
SimpleQueryStackDumpIterator::readFuzzy(const char *&p) {
    _curr_index_name = read_stringref(p);
    _curr_term = read_stringref(p); // fuzzy term
    _extraIntArg1 = readCompressedPositiveInt(p); // maxEditDistance
    _extraIntArg2 = readCompressedPositiveInt(p); // prefixLength
    _currArity = 0;
}

std::unique_ptr<query::PredicateQueryTerm>
SimpleQueryStackDumpIterator::getPredicateQueryTerm()
{
    return std::move(_predicate_query_term);
}

void
SimpleQueryStackDumpIterator::read_string_in(const char*& p)
{
    uint32_t num_terms = readCompressedPositiveInt(p);
    _currArity = 0;
    _curr_index_name = read_stringref(p);
    _curr_term = vespalib::stringref();
    auto terms = std::make_unique<StringTermVector>(num_terms);
    for (uint32_t i = 0; i < num_terms; ++i) {
        terms->addTerm(read_stringref(p));
    }
    _terms = std::move(terms);
}

void
SimpleQueryStackDumpIterator::read_numeric_in(const char*& p)
{
    uint32_t num_terms = readCompressedPositiveInt(p);
    _currArity = 0;
    _curr_index_name = read_stringref(p);
    _curr_term = vespalib::stringref();
    auto terms = std::make_unique<IntegerTermVector>(num_terms);
    for (uint32_t i = 0; i < num_terms; ++i) {
        terms->addTerm(read_value<int64_t>(p));
    }
    _terms = std::move(terms);
}

std::unique_ptr<query::TermVector>
SimpleQueryStackDumpIterator::get_terms()
{
    return std::move(_terms);
}

}
