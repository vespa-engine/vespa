// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "stackdumpiterator.h"
#include <vespa/searchlib/query/tree/integer_term_vector.h>
#include <vespa/searchlib/query/tree/predicate_query_term.h>
#include <vespa/searchlib/query/tree/string_term_vector.h>
#include <vespa/vespalib/util/compress.h>
#include <vespa/vespalib/objects/nbo.h>
#include <cassert>
#include <cstring>

using search::query::IntegerTermVector;
using search::query::PredicateQueryTerm;
using search::query::StringTermVector;

namespace search {

SimpleQueryStackDumpIterator::SimpleQueryStackDumpIterator(std::string_view buf)
    : _buf(buf.begin()),
      _bufEnd(buf.end()),
      _currPos(0),
      _currEnd(0)
{
}

SimpleQueryStackDumpIterator::~SimpleQueryStackDumpIterator() = default;

std::string_view
SimpleQueryStackDumpIterator::read_string_view(const char *&p)
{
    uint64_t len = readCompressedPositiveInt(p);
    if ((p + len) > _bufEnd) throw false;
    std::string_view result(p, len);
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
    _d.itemType = static_cast<ParseItem::ItemType>(type_code);

    if (ParseItem::GetFeature_Weight(typefield)) {
        int64_t tmpLong = readCompressedInt(p);
        _d.weight.setPercent(tmpLong);
    } else {
        _d.weight.setPercent(100);
    }
    if (__builtin_expect(ParseItem::getFeature_UniqueId(typefield), false)) {
        _d.uniqueId = readCompressedPositiveInt(p);
    } else {
        _d.uniqueId = 0;
    }
    /** flags of the current item **/
    uint8_t currFlags = 0;
    if (__builtin_expect(ParseItem::getFeature_Flags(typefield), false)) {
        if ((p + sizeof(uint8_t)) > _bufEnd) return false;
        currFlags = (uint8_t)*p++;
    }
    _d.noRankFlag = (currFlags & ParseItem::ItemFlags::IFLAG_NORANK) != 0;
    _d.noPositionDataFlag = (currFlags & ParseItem::ItemFlags::IFLAG_NOPOSITIONDATA) != 0;
    _d.isSpecialTokenFlag = (currFlags & ParseItem::IFLAG_SPECIALTOKEN) != 0;

    _d.creaFilterFlag = (currFlags & ParseItem::ItemFlags::IFLAG_FILTER) != 0;
    _d.prefix_match_semantics_flag = (currFlags & ParseItem::ItemFlags::IFLAG_PREFIX_MATCH) != 0;
    _d.term_view = std::string_view();

    switch (_d.itemType) {
    case ParseItem::ITEM_OR:
    case ParseItem::ITEM_EQUIV:
    case ParseItem::ITEM_AND:
    case ParseItem::ITEM_NOT:
    case ParseItem::ITEM_RANK:
        _d.arity = readCompressedPositiveInt(p);
        _d.index_view = std::string_view();
        break;

    case ParseItem::ITEM_NEAR:
    case ParseItem::ITEM_ONEAR:
        _d.arity = readCompressedPositiveInt(p);
        _d.nearDistance = readCompressedPositiveInt(p);
        _d.index_view = std::string_view();
        break;

    case ParseItem::ITEM_WEAK_AND:
        _d.arity = readCompressedPositiveInt(p);
        _d.targetHits = readCompressedPositiveInt(p); // targetNumHits
        _d.index_view = read_string_view(p);
        break;
    case ParseItem::ITEM_SAME_ELEMENT:
        _d.arity = readCompressedPositiveInt(p);
        _d.index_view = read_string_view(p);
        break;

    case ParseItem::ITEM_PURE_WEIGHTED_STRING:
        _d.term_view = read_string_view(p);
        _d.arity = 0;
        break;
    case ParseItem::ITEM_PURE_WEIGHTED_LONG:
        _d.integerTerm = read_value<int64_t>(p);
        _d.arity = 0;
        break;
    case ParseItem::ITEM_WORD_ALTERNATIVES:
        _d.index_view = read_string_view(p);
        _d.arity = readCompressedPositiveInt(p);
        break;
    case ParseItem::ITEM_NUMTERM:
    case ParseItem::ITEM_GEO_LOCATION_TERM:
    case ParseItem::ITEM_TERM:
    case ParseItem::ITEM_PREFIXTERM:
    case ParseItem::ITEM_SUBSTRINGTERM:
    case ParseItem::ITEM_EXACTSTRINGTERM:
    case ParseItem::ITEM_SUFFIXTERM:
    case ParseItem::ITEM_REGEXP:
        _d.index_view = read_string_view(p);
        _d.term_view = read_string_view(p);
        _d.arity = 0;
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
        _d.arity = 0;
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
    _d.index_view = read_string_view(p);
    _d.predicateQueryTerm = std::make_unique<PredicateQueryTerm>();

    size_t count = readCompressedPositiveInt(p);
    for (size_t i = 0; i < count; ++i) {
        std::string_view key = read_string_view(p);
        std::string_view value = read_string_view(p);
        uint64_t sub_queries = read_value<uint64_t>(p);
        _d.predicateQueryTerm->addFeature(std::string(key), std::string(value), sub_queries);
    }
    count = readCompressedPositiveInt(p);
    for (size_t i = 0; i < count; ++i) {
        std::string_view key = read_string_view(p);
        uint64_t value = read_value<uint64_t>(p);
        uint64_t sub_queries = read_value<uint64_t>(p);
        _d.predicateQueryTerm->addRangeFeature(std::string(key), value, sub_queries);
    }
}

void
SimpleQueryStackDumpIterator::readNN(const char *& p) {
    _d.index_view = read_string_view(p);
    _d.term_view = read_string_view(p); // query_tensor_name
    _d.targetHits = readCompressedPositiveInt(p); // targetNumHits
    uint32_t tmp = readCompressedPositiveInt(p); // allow_approximate
    // XXX: remove later when QRS doesn't send this extra flag
    _d.allowApproximateFlag = ((tmp & ~0x40) != 0);
    _d.exploreAdditionalHits = readCompressedPositiveInt(p); // explore_additional_hits
    // QRS always sends this now:
    _d.distanceThreshold = read_value<double>(p); // distance threshold
    _d.arity = 0;
}

void
SimpleQueryStackDumpIterator::readComplexTerm(const char *& p) {
    _d.arity = readCompressedPositiveInt(p);
    _d.index_view = read_string_view(p);
    if (getType() == ParseItem::ITEM_WAND) {
        _d.targetHits = readCompressedPositiveInt(p); // targetNumHits
        _d.scoreThreshold = read_value<double>(p); // scoreThreshold
        _d.thresholdBoostFactor = read_value<double>(p); // thresholdBoostFactor
    }
}

void
SimpleQueryStackDumpIterator::readFuzzy(const char *&p) {
    _d.index_view = read_string_view(p);
    _d.term_view = read_string_view(p); // fuzzy term
    _d.fuzzy_max_edit_distance = readCompressedPositiveInt(p); // maxEditDistance
    _d.fuzzy_prefix_lock_length = readCompressedPositiveInt(p); // prefixLength
    _d.arity = 0;
}

void
SimpleQueryStackDumpIterator::read_string_in(const char*& p)
{
    uint32_t num_terms = readCompressedPositiveInt(p);
    _d.arity = 0;
    _d.index_view = read_string_view(p);
    auto terms = std::make_unique<StringTermVector>(num_terms);
    for (uint32_t i = 0; i < num_terms; ++i) {
        terms->addTerm(read_string_view(p));
    }
    _d.termVector = std::move(terms);
}

void
SimpleQueryStackDumpIterator::read_numeric_in(const char*& p)
{
    uint32_t num_terms = readCompressedPositiveInt(p);
    _d.arity = 0;
    _d.index_view = read_string_view(p);
    auto terms = std::make_unique<IntegerTermVector>(num_terms);
    for (uint32_t i = 0; i < num_terms; ++i) {
        terms->addTerm(read_value<int64_t>(p));
    }
    _d.termVector = std::move(terms);
}

}
