// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simplequerystackitem.h"
#include <vespa/vespalib/objects/nbo.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <cassert>

namespace search {

SimpleQueryStackItem::SimpleQueryStackItem()
  : _next(NULL),
    _arg1(0),
    _arg2(0),
    _arg3(0),
    _type(ITEM_UNDEF),
    _arity(0),
    _indexName(),
    _term()
{}

namespace {

void assert_term_type(ParseItem::ItemType type) {
    assert(type == ParseItem::ITEM_TERM ||
           type == ParseItem::ITEM_NUMTERM ||
           type == ParseItem::ITEM_NEAREST_NEIGHBOR ||
           type == ParseItem::ITEM_GEO_LOCATION_TERM ||
           type == ParseItem::ITEM_PREFIXTERM ||
           type == ParseItem::ITEM_SUBSTRINGTERM ||
           type == ParseItem::ITEM_SUFFIXTERM ||
           type == ParseItem::ITEM_PURE_WEIGHTED_STRING ||
           type == ParseItem::ITEM_PURE_WEIGHTED_LONG ||
           type == ParseItem::ITEM_EXACTSTRINGTERM ||
           type == ParseItem::ITEM_PREDICATE_QUERY);
    (void) type;
}

void assert_arity_type(ParseItem::ItemType type) {
    // types with arity, but without an index name:
    assert(type == ParseItem::ITEM_OR ||
           type == ParseItem::ITEM_WEAK_AND ||
           type == ParseItem::ITEM_EQUIV ||
           type == ParseItem::ITEM_AND ||
           type == ParseItem::ITEM_NOT ||
           type == ParseItem::ITEM_RANK ||
           type == ParseItem::ITEM_ANY ||
           type == ParseItem::ITEM_NEAR ||
           type == ParseItem::ITEM_ONEAR);
    (void) type;
}

void assert_arity_and_index_type(ParseItem::ItemType type) {
    // types with arity and an index name:
    assert(type == ParseItem::ITEM_PHRASE ||
           type == ParseItem::ITEM_SAME_ELEMENT ||
           type == ParseItem::ITEM_WEIGHTED_SET ||
           type == ParseItem::ITEM_DOT_PRODUCT ||
           type == ParseItem::ITEM_WAND ||
           type == ParseItem::ITEM_WORD_ALTERNATIVES);
    (void) type;
}

int64_t term_as_n64(vespalib::stringref term) {
    int64_t tmp;
    vespalib::asciistream generatedTerm(term);
    generatedTerm >> tmp;
    return vespalib::nbo::n2h(tmp);
}

} // namespace <unnamed>


SimpleQueryStackItem::SimpleQueryStackItem(ItemType type, int arity) : SimpleQueryStackItem()
{
    assert_arity_type(type);
    SetType(type);
    _arity = arity;
}

SimpleQueryStackItem::SimpleQueryStackItem(ItemType type, int arity, const char *idx) : SimpleQueryStackItem()
{
    assert_arity_and_index_type(type);
    SetType(type);
    _arity = arity;
    SetIndex(idx);
}

SimpleQueryStackItem::SimpleQueryStackItem(ItemType type, const char *term) : SimpleQueryStackItem()
{
    assert_term_type(type);
    SetType(type);
    SetTerm(term);
}

SimpleQueryStackItem::~SimpleQueryStackItem()
{
    delete _next;
}

void
SimpleQueryStackItem::AppendBuffer(RawBuf *buf) const
{
    // Calculate lengths
    uint32_t indexLen = _indexName.size();
    uint32_t termLen = _term.size();
    double nboVal = 0.0;

    // Put the values into the buffer.
    buf->append(_type);
    switch (Type()) {
    case ITEM_OR:
    case ITEM_EQUIV:
    case ITEM_AND:
    case ITEM_NOT:
    case ITEM_RANK:
    case ITEM_ANY:
        buf->appendCompressedPositiveNumber(_arity);
        break;
    case ITEM_NEAR:
    case ITEM_ONEAR:
        buf->appendCompressedPositiveNumber(_arity);
        buf->appendCompressedPositiveNumber(_arg1);
        break;
    case ITEM_SAME_ELEMENT:
    case ITEM_WEIGHTED_SET:
    case ITEM_DOT_PRODUCT:
    case ITEM_PHRASE:
        buf->appendCompressedPositiveNumber(_arity);
        buf->appendCompressedPositiveNumber(indexLen);
        buf->append(_indexName.c_str(), indexLen);
        break;
    case ITEM_WORD_ALTERNATIVES:
        buf->appendCompressedPositiveNumber(indexLen);
        buf->append(_indexName.c_str(), indexLen);
        buf->appendCompressedPositiveNumber(_arity);
        break;
    case ITEM_WEAK_AND:
        buf->appendCompressedPositiveNumber(_arity);
        buf->appendCompressedPositiveNumber(_arg1);
        buf->appendCompressedPositiveNumber(indexLen);
        buf->append(_indexName.c_str(), indexLen);
        break;
    case ITEM_WAND:
        buf->appendCompressedPositiveNumber(_arity);
        buf->appendCompressedPositiveNumber(indexLen);
        buf->append(_indexName.c_str(), indexLen);
        buf->appendCompressedPositiveNumber(_arg1); // targetNumHits
        nboVal = vespalib::nbo::n2h(_arg2);
        buf->append(&nboVal, sizeof(nboVal)); // scoreThreshold
        nboVal = vespalib::nbo::n2h(_arg3);
        buf->append(&nboVal, sizeof(nboVal)); // thresholdBoostFactor
        break;
    case ITEM_TERM:
    case ITEM_NUMTERM:
    case ITEM_GEO_LOCATION_TERM:
    case ITEM_PREFIXTERM:
    case ITEM_SUBSTRINGTERM:
    case ITEM_EXACTSTRINGTERM:
    case ITEM_SUFFIXTERM:
    case ITEM_REGEXP:
        buf->appendCompressedPositiveNumber(indexLen);
        buf->append(_indexName.c_str(), indexLen);
        buf->appendCompressedPositiveNumber(termLen);
        buf->append(_term.c_str(), termLen);
        break;
    case ITEM_PURE_WEIGHTED_STRING:
        buf->appendCompressedPositiveNumber(termLen);
        buf->append(_term.c_str(), termLen);
        break;
    case ITEM_PURE_WEIGHTED_LONG:
        {
            int64_t tmp = term_as_n64(_term);
            buf->append(&tmp, sizeof(int64_t));
        }
        break;
    case ITEM_NEAREST_NEIGHBOR:
        buf->appendCompressedPositiveNumber(indexLen);
        buf->append(_indexName.c_str(), indexLen);
        buf->appendCompressedPositiveNumber(termLen);
        buf->append(_term.c_str(), termLen);
        buf->appendCompressedPositiveNumber(_arg1); // targetNumHits
        buf->appendCompressedPositiveNumber(_arg2); // allow_approximate
        buf->appendCompressedPositiveNumber(_arg3); // explore_additional_hits
        break;
    case ITEM_PREDICATE_QUERY: // not handled at all here
    case ITEM_MAX:
    case ITEM_UNDEF:
        abort();
        break;
    }
}

}
