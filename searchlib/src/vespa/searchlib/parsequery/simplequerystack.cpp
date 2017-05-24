// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Creation date: 2000-05-15
 * Implementation of the simple query stack.
 *
 *   Copyright (C) 1997-2003 Fast Search & Transfer ASA
 *   Copyright (C) 2003 Overture Services Norway AS
 *               ALL RIGHTS RESERVED
 */
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
#include <vespa/vespalib/util/vstringfmt.h>
#include <vespa/vespalib/util/compress.h>
#include <vespa/vespalib/objects/nbo.h>
#include <vespa/searchlib/parsequery/simplequerystack.h>

LOG_SETUP(".search.simplequerystack");

using vespalib::make_vespa_string;

namespace search {

SimpleQueryStack::SimpleQueryStack()
    : _numItems(0),
      _stack(NULL),
      _FP_queryOK(true)
{
}

SimpleQueryStack::~SimpleQueryStack()
{
    delete _stack;
}

void
SimpleQueryStack::Push(search::ParseItem *item)
{
    // Check if query OK for FirstPage
    _FP_queryOK &=
        ( item->Type() != search::ParseItem::ITEM_UNDEF
         && item->Type() != search::ParseItem::ITEM_PAREN
          );


    item->_next = _stack;
    _stack = item;

    _numItems++;
}

search::ParseItem *
SimpleQueryStack::Pop()
{
    search::ParseItem *item = _stack;
    if (_stack != NULL) {
        _numItems--;
        _stack = _stack->_next;
        item->_next = NULL;
    }
    return item;
}

void
SimpleQueryStack::AppendBuffer(search::RawBuf *buf) const
{
    for (search::ParseItem *item = _stack; item != NULL; item = item->_next) {
        item->AppendBuffer(buf);
    }
}

size_t
SimpleQueryStack::GetBufferLen() const
{
    size_t result;

    result = 0;
    for (const search::ParseItem *item = _stack;
         item != NULL; item = item->_next) {
        result += item->GetBufferLen();
    }

    return result;
}

uint32_t
SimpleQueryStack::GetSize()
{
    return _numItems;
}

bool
SimpleQueryStack::_FP_isAllowed()
{
    return _FP_queryOK;
}

class ItemName {
public:
    ItemName() {
        memset(_name, 'X', sizeof(_name));
        _name[search::ParseItem::ITEM_OR] = '|';
        _name[search::ParseItem::ITEM_WEAK_AND] = 'w';
        _name[search::ParseItem::ITEM_EQUIV] = 'E';
        _name[search::ParseItem::ITEM_AND] = '&';
        _name[search::ParseItem::ITEM_NOT] = '-';
        _name[search::ParseItem::ITEM_ANY] = '?';
        _name[search::ParseItem::ITEM_RANK] = '%';
        _name[search::ParseItem::ITEM_NEAR] = 'N';
        _name[search::ParseItem::ITEM_ONEAR] = 'O';
        _name[search::ParseItem::ITEM_NUMTERM] = '#';
        _name[search::ParseItem::ITEM_TERM] = 't';
        _name[search::ParseItem::ITEM_PURE_WEIGHTED_STRING] = 'T';
        _name[search::ParseItem::ITEM_PURE_WEIGHTED_LONG] = 'L';
        _name[search::ParseItem::ITEM_PREFIXTERM] = '*';
        _name[search::ParseItem::ITEM_SUBSTRINGTERM] = 's';
        _name[search::ParseItem::ITEM_EXACTSTRINGTERM] = 'e';
        _name[search::ParseItem::ITEM_SUFFIXTERM] = 'S';
        _name[search::ParseItem::ITEM_PHRASE] = '"';
        _name[search::ParseItem::ITEM_WEIGHTED_SET] = 'W';
        _name[search::ParseItem::ITEM_DOT_PRODUCT] = 'D';
        _name[search::ParseItem::ITEM_WAND] = 'A';
        _name[search::ParseItem::ITEM_PREDICATE_QUERY] = 'P';
        _name[search::ParseItem::ITEM_REGEXP] = '^';
    }
    char operator[] (search::ParseItem::ItemType i) const { return _name[i]; }
    char operator[] (size_t i) const { return _name[i]; }
private:
    char _name[search::ParseItem::ITEM_MAX];
};

static ItemName _G_ItemName;

vespalib::string
SimpleQueryStack::StackbufToString(const vespalib::stringref &theBuf)
{
    vespalib::string result;

    /*
     * This is a slightly bogus estimate of the size required. It should
     * be enough in most cases, but it is possible to break it in rare and
     * artificial circumstances.
     *
     * The simple operators use 8 bytes in the buffer.
     *   The string representation has 3 overhead chars, leaving 5 chars
     *   for the printed representation of the arity, i.e. < 10^5.
     *
     * The phrase operator uses 12 bytes + the length of the index string.
     *   The string representation has 5 overhead chars, leaving 7 chars
     *   for the total printed representation of the length of the index.
     *   If the index is 0, then the arity may use 6 chars, i.e. < 10^6.
     *
     * The term operator uses 12 bytes + the length of the index and term string.
     *   The string representation has 6 overhead chars, leaving 6 chars
     *   for the total printed representation of the index and term lengths.
     *   If for instance the index is 0, then the term must be shorter
     *   than 10^5 characters.
     */

    uint8_t rawtype = 0;
    uint32_t type = 0, arity = 0, arg1 = 0;
    const char *idxRef;
    const char *termRef;
    uint32_t idxRefLen;
    uint32_t termRefLen;

    const char *p = theBuf.begin();
    const char *ep = theBuf.end();
    uint64_t tmp(0);
    uint8_t flags(0);
    while (p < ep) {
        vespalib::string metaStr;
        rawtype = *p++;
        type = search::ParseItem::GetType(rawtype);
        if (search::ParseItem::GetFeature_Weight(rawtype)) {
            int64_t tmpLong(0);
            p += vespalib::compress::Integer::decompress(tmpLong, p);
            metaStr.append("(w:");
            metaStr.append(make_vespa_string("%ld", tmpLong));
            metaStr.append(")");
        }
        if (search::ParseItem::getFeature_UniqueId(rawtype)) {
            p += vespalib::compress::Integer::decompressPositive(tmp, p);
            metaStr.append("(u:");
            metaStr.append(make_vespa_string("%ld", tmp));
            metaStr.append(")");
        }
        if (search::ParseItem::getFeature_Flags(rawtype)) {
            flags = *p++;
            metaStr.append("(f:");
            metaStr.append(make_vespa_string("%d", flags));
            metaStr.append(")");
        }
        if (search::ParseItem::GetCreator(flags) != search::ParseItem::CREA_ORIG) {
            metaStr.append("(c:");
            metaStr.append(make_vespa_string("%d", search::ParseItem::GetCreator(flags)));
            metaStr.append(")");
        }

        metaStr.append('/');
        result.append(metaStr);

        switch (type) {
        case search::ParseItem::ITEM_OR:
        case search::ParseItem::ITEM_AND:
        case search::ParseItem::ITEM_EQUIV:
        case search::ParseItem::ITEM_NOT:
        case search::ParseItem::ITEM_RANK:
        case search::ParseItem::ITEM_ANY:
            p += vespalib::compress::Integer::decompressPositive(tmp, p);
            arity = tmp;
            result.append(make_vespa_string("%c/%d~", _G_ItemName[type], arity));
            break;
        case search::ParseItem::ITEM_WEAK_AND:
        case search::ParseItem::ITEM_NEAR:
        case search::ParseItem::ITEM_ONEAR:
            p += vespalib::compress::Integer::decompressPositive(tmp, p);
            arity = tmp;
            p += vespalib::compress::Integer::decompressPositive(tmp, p);
            arg1 = tmp;
            if (type == search::ParseItem::ITEM_WEAK_AND) {
                p += vespalib::compress::Integer::decompressPositive(tmp, p);
                idxRefLen = tmp;
                idxRef = p;
                p += idxRefLen;
                result.append(make_vespa_string("%c/%d/%d/%d:%.*s~", _G_ItemName[type], arity, arg1, idxRefLen, idxRefLen, idxRef));
            } else {
                result.append(make_vespa_string("%c/%d/%d~", _G_ItemName[type], arity, arg1));
            }
            break;

        case search::ParseItem::ITEM_NUMTERM:
        case search::ParseItem::ITEM_TERM:
        case search::ParseItem::ITEM_PREFIXTERM:
        case search::ParseItem::ITEM_SUBSTRINGTERM:
        case search::ParseItem::ITEM_EXACTSTRINGTERM:
        case search::ParseItem::ITEM_SUFFIXTERM:
        case search::ParseItem::ITEM_REGEXP:
            p += vespalib::compress::Integer::decompressPositive(tmp, p);
            idxRefLen = tmp;
            idxRef = p;
            p += idxRefLen;
            p += vespalib::compress::Integer::decompressPositive(tmp, p);
            termRefLen = tmp;
            termRef = p;
            p += termRefLen;
            result.append(make_vespa_string("%c/%d:%.*s/%d:%.*s~", _G_ItemName[type],
                                            idxRefLen, idxRefLen, idxRef,
                                            termRefLen, termRefLen, termRef));
            break;
        case search::ParseItem::ITEM_PURE_WEIGHTED_STRING:
            p += vespalib::compress::Integer::decompressPositive(tmp, p);
            termRefLen = tmp;
            termRef = p;
            p += termRefLen;
            result.append(make_vespa_string("%c/%d:%.*s~", _G_ItemName[type],
                                            termRefLen, termRefLen, termRef));
            break;

        case search::ParseItem::ITEM_PURE_WEIGHTED_LONG:
            tmp = vespalib::nbo::n2h(*reinterpret_cast<const uint64_t *>(p));
            p += sizeof(uint64_t);
            result.append(make_vespa_string("%c/%lu", _G_ItemName[type], tmp));
            break;

        case search::ParseItem::ITEM_PHRASE:
        case search::ParseItem::ITEM_WEIGHTED_SET:
        case search::ParseItem::ITEM_DOT_PRODUCT:
        case search::ParseItem::ITEM_WAND:
            p += vespalib::compress::Integer::decompressPositive(tmp, p);
            arity = tmp;
            p += vespalib::compress::Integer::decompressPositive(tmp, p);
            idxRefLen = tmp;
            idxRef = p;
            p += idxRefLen;
            if (type == search::ParseItem::ITEM_WAND) {
                p += vespalib::compress::Integer::decompressPositive(tmp, p);
                uint32_t targetNumHits = tmp;
                double scoreThreshold = vespalib::nbo::n2h(*reinterpret_cast<const double *>(p)); 
                p += sizeof(double);
                double thresholdBoostFactor = vespalib::nbo::n2h(*reinterpret_cast<const double *>(p)); // thresholdBoostFactor
                p += sizeof(double);
                result.append(make_vespa_string("%c/%d/%d:%.*s(%u,%f,%f)~", _G_ItemName[type], arity, idxRefLen,
                                                idxRefLen, idxRef, targetNumHits, scoreThreshold, thresholdBoostFactor));
            } else {
                result.append(make_vespa_string("%c/%d/%d:%.*s~", _G_ItemName[type], arity, idxRefLen,
                                                idxRefLen, idxRef));
            }
            break;

        case search::ParseItem::ITEM_PREDICATE_QUERY:
        {
            idxRefLen = static_cast<uint32_t>(ReadCompressedPositiveInt(p));
            idxRef = p;
            p += idxRefLen;
            size_t feature_count = ReadCompressedPositiveInt(p);
            result.append(make_vespa_string(
                    "%c/%d:%.*s/%zu(", _G_ItemName[type], idxRefLen, idxRefLen, idxRef, feature_count));
            for (size_t i = 0; i < feature_count; ++i) {
                vespalib::string key = ReadString(p);
                vespalib::string value = ReadString(p);
                uint64_t sub_queries = ReadUint64(p);
                result.append(make_vespa_string("%s:%s:%" PRIx64, key.c_str(), value.c_str(), sub_queries));
                if (i < feature_count - 1) {
                    result.append(',');
                }
            }

            size_t range_feature_count = ReadCompressedPositiveInt(p);
            result.append(make_vespa_string(")/%zu(", range_feature_count));
            for (size_t i = 0; i < range_feature_count; ++i) {
                vespalib::string key = ReadString(p);
                uint64_t value = ReadUint64(p);
                uint64_t sub_queries = ReadUint64(p);
                result.append(make_vespa_string("%s:%" PRIu64 ":%" PRIx64, key.c_str(), value, sub_queries));
                if (i < range_feature_count - 1) {
                    result.append(',');
                }
            }
            result.append(")~");
            break;
        }

        default:
            LOG(error, "Unhandled type %d", type);
            abort();
        }
    }
    return result;
}

vespalib::string
SimpleQueryStack::ReadString(const char *&p)
{
    uint64_t tmp;
    p += vespalib::compress::Integer::decompressPositive(tmp, p);
    vespalib::string s(p, tmp);
    p += s.size();
    return s;
}

uint64_t
SimpleQueryStack::ReadUint64(const char *&p)
{
    uint64_t l = static_cast<uint64_t>(vespalib::nbo::n2h(*(const uint64_t *)p));
    p += sizeof(uint64_t);
    return l;
}

uint64_t
SimpleQueryStack::ReadCompressedPositiveInt(const char *&p)
{
    uint64_t tmp;
    p += vespalib::compress::Integer::decompressPositive(tmp, p);
    return tmp;
}

} // namespace search
