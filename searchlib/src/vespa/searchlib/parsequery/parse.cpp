// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "parse.h"
#include <vespa/vespalib/objects/nbo.h>
#include <cassert>

namespace search {

#define PARSEITEM_DEFAULT_CONSTRUCTOR_LIST \
  _next(NULL), \
  _sibling(NULL), \
  _weight(100), \
  _uniqueId(0), \
  _arg1(0), \
  _arg2(0), \
  _arg3(0), \
  _type(ITEM_UNDEF), \
  _flags(0), \
  _arity(0), \
  _indexName(), \
  _term()


ParseItem::ParseItem(ItemType type, int arity)
    : PARSEITEM_DEFAULT_CONSTRUCTOR_LIST
{
    assert(type==ITEM_OR || type==ITEM_WEAK_AND || type==ITEM_EQUIV || type==ITEM_AND || type==ITEM_NOT
           || type==ITEM_RANK || type==ITEM_ANY || type==ITEM_NEAR || type==ITEM_ONEAR);
    SetType(type);
    _arity = arity;
}

ParseItem::ParseItem(ItemType type, int arity, const char *idx)
    : PARSEITEM_DEFAULT_CONSTRUCTOR_LIST
{
    assert(type==ITEM_PHRASE || type==ITEM_SAME_ELEMENT || type==ITEM_WEIGHTED_SET
           || type==ITEM_DOT_PRODUCT || type==ITEM_WAND);
    SetType(type);
    _arity = arity;
    SetIndex(idx);
}

namespace {

void assert_type(ParseItem::ItemType type)
{
    assert(type == ParseItem::ITEM_TERM ||
           type == ParseItem::ITEM_NUMTERM ||
           type == ParseItem::ITEM_PREFIXTERM ||
           type == ParseItem::ITEM_SUBSTRINGTERM ||
           type == ParseItem::ITEM_SUFFIXTERM ||
           type == ParseItem::ITEM_PURE_WEIGHTED_STRING ||
           type == ParseItem::ITEM_PURE_WEIGHTED_LONG ||
           type == ParseItem::ITEM_EXACTSTRINGTERM ||
           type == ParseItem::ITEM_PREDICATE_QUERY);
    (void) type;
}

}

ParseItem::ParseItem(ItemType type, const vespalib::stringref & idx, const char *term)
    : PARSEITEM_DEFAULT_CONSTRUCTOR_LIST
{
    assert_type(type);
    SetType(type);
    SetIndex(idx.c_str());
    SetTerm(term);
}

ParseItem::ParseItem(ItemType type, const char *term)
    : PARSEITEM_DEFAULT_CONSTRUCTOR_LIST
{
    assert_type(type);
    SetType(type);
    SetTerm(term);
}

ParseItem::~ParseItem()
{
    delete _next;
    delete _sibling;
}

void
ParseItem::AppendBuffer(RawBuf *buf) const
{
    // Calculate the length of the buffer.
    uint32_t indexLen = _indexName.size();
    uint32_t termLen = _term.size();

    // Put the values into the buffer.
    buf->append(_type);
    if (Feature_Weight()) { // this item has weight
        buf->appendCompressedNumber(_weight.percent());
    }
    if (feature_UniqueId()) {
        buf->appendCompressedPositiveNumber(_uniqueId);
    }
    if (feature_Flags()) {
        buf->append(_flags);
    }
    switch (Type()) {
    case ITEM_OR:
    case ITEM_EQUIV:
    case ITEM_AND:
    case ITEM_NOT:
    case ITEM_RANK:
    case ITEM_ANY:
        buf->appendCompressedPositiveNumber(_arity);
        break;
    case ITEM_WEAK_AND:
    case ITEM_NEAR:
    case ITEM_ONEAR:
        buf->appendCompressedPositiveNumber(_arity);
        buf->appendCompressedPositiveNumber(_arg1);
        if (Type() == ITEM_WEAK_AND) {
            buf->appendCompressedPositiveNumber(indexLen);
            if (indexLen != 0) {
                buf->append(_indexName.c_str(), indexLen);
            }
        }
        break;
    case ITEM_WEIGHTED_SET:
    case ITEM_DOT_PRODUCT:
    case ITEM_WAND:
    case ITEM_PHRASE:
    case ITEM_SAME_ELEMENT:
        buf->appendCompressedPositiveNumber(_arity);
        buf->appendCompressedPositiveNumber(indexLen);
        if (indexLen != 0) {
            buf->append(_indexName.c_str(), indexLen);
        }
        if (Type() == ITEM_WAND) {
            buf->appendCompressedPositiveNumber(_arg1); // targetNumHits
            double nboVal = vespalib::nbo::n2h(_arg2);
            buf->append(&nboVal, sizeof(nboVal)); // scoreThreshold
            nboVal = vespalib::nbo::n2h(_arg3);
            buf->append(&nboVal, sizeof(nboVal)); // thresholdBoostFactor
        }
        break;
    case ITEM_TERM:
    case ITEM_NUMTERM:
    case ITEM_PREFIXTERM:
    case ITEM_SUBSTRINGTERM:
    case ITEM_EXACTSTRINGTERM:
    case ITEM_SUFFIXTERM:
    case ITEM_REGEXP:
        buf->appendCompressedPositiveNumber(indexLen);
        if (indexLen != 0) {
            buf->append(_indexName.c_str(), indexLen);
        }
        buf->appendCompressedPositiveNumber(termLen);
        if (termLen != 0) {
            buf->append(_term.c_str(), termLen);
        }
        break;
    case ITEM_UNDEF:
    default:
        break;
    }
}

size_t
ParseItem::GetBufferLen() const
{
    // Calculate the length of the buffer.
    uint32_t indexLen = _indexName.size();
    uint32_t termLen = _term.size();

    uint32_t len = sizeof(uint8_t); // type field
    if (Feature_Weight()) {
        len += sizeof(uint32_t);
    }
    if (feature_UniqueId()) {
        len += sizeof(uint32_t);
    }
    if (feature_Flags()) {
        len += sizeof(uint8_t);
    }

    // Put the values into the buffer.
    switch (Type()) {
    case ITEM_OR:
    case ITEM_EQUIV:
    case ITEM_AND:
    case ITEM_NOT:
    case ITEM_RANK:
    case ITEM_ANY:
        len += sizeof(uint32_t);
        break;
    case ITEM_NEAR:
    case ITEM_ONEAR:
        len += sizeof(uint32_t) * 2;
        break;
    case ITEM_WEAK_AND:
        len += sizeof(uint32_t) * 3 + indexLen;
        break;
    case ITEM_WEIGHTED_SET:
    case ITEM_DOT_PRODUCT:
    case ITEM_PHRASE:
    case ITEM_SAME_ELEMENT:
        len += sizeof(uint32_t) * 2 + indexLen;
        break;
    case ITEM_WAND:
        len += sizeof(uint32_t) * 4 + indexLen;
        break;
    case ITEM_TERM:
    case ITEM_NUMTERM:
    case ITEM_PREFIXTERM:
    case ITEM_SUBSTRINGTERM:
    case ITEM_EXACTSTRINGTERM:
    case ITEM_SUFFIXTERM:
    case ITEM_REGEXP:
        len += sizeof(uint32_t) * 2 + indexLen + termLen;
        break;
    case ITEM_PURE_WEIGHTED_STRING:
        len += sizeof(uint32_t) + termLen;
        break;
    case ITEM_PURE_WEIGHTED_LONG:
        len += sizeof(uint64_t);
        break;
    case ITEM_UNDEF:
    default:
        break;
    }
    return len;
}

}
