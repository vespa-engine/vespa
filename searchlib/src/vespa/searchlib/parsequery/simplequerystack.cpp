// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simplequerystack.h"
#include <vespa/vespalib/util/compress.h>
#include <vespa/vespalib/objects/nbo.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>
LOG_SETUP(".search.simplequerystack");

using vespalib::make_string;

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
SimpleQueryStack::Push(ParseItem *item)
{
    // Check if query OK for FirstPage
    _FP_queryOK &=
        ( item->Type() != ParseItem::ITEM_UNDEF
         && item->Type() != ParseItem::ITEM_PAREN
          );


    item->_next = _stack;
    _stack = item;

    _numItems++;
}

ParseItem *
SimpleQueryStack::Pop()
{
    ParseItem *item = _stack;
    if (_stack != NULL) {
        _numItems--;
        _stack = _stack->_next;
        item->_next = NULL;
    }
    return item;
}

void
SimpleQueryStack::AppendBuffer(RawBuf *buf) const
{
    for (ParseItem *item = _stack; item != NULL; item = item->_next) {
        item->AppendBuffer(buf);
    }
}

size_t
SimpleQueryStack::GetBufferLen() const
{
    size_t result;

    result = 0;
    for (const ParseItem *item = _stack;
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
        _name[ParseItem::ITEM_OR] = '|';
        _name[ParseItem::ITEM_WEAK_AND] = 'w';
        _name[ParseItem::ITEM_EQUIV] = 'E';
        _name[ParseItem::ITEM_AND] = '&';
        _name[ParseItem::ITEM_NOT] = '-';
        _name[ParseItem::ITEM_ANY] = '?';
        _name[ParseItem::ITEM_RANK] = '%';
        _name[ParseItem::ITEM_NEAR] = 'N';
        _name[ParseItem::ITEM_ONEAR] = 'O';
        _name[ParseItem::ITEM_NUMTERM] = '#';
        _name[ParseItem::ITEM_TERM] = 't';
        _name[ParseItem::ITEM_PURE_WEIGHTED_STRING] = 'T';
        _name[ParseItem::ITEM_PURE_WEIGHTED_LONG] = 'L';
        _name[ParseItem::ITEM_PREFIXTERM] = '*';
        _name[ParseItem::ITEM_SUBSTRINGTERM] = 's';
        _name[ParseItem::ITEM_EXACTSTRINGTERM] = 'e';
        _name[ParseItem::ITEM_SUFFIXTERM] = 'S';
        _name[ParseItem::ITEM_PHRASE] = '"';
        _name[ParseItem::ITEM_SAME_ELEMENT] = 'M';
        _name[ParseItem::ITEM_WEIGHTED_SET] = 'W';
        _name[ParseItem::ITEM_DOT_PRODUCT] = 'D';
        _name[ParseItem::ITEM_WAND] = 'A';
        _name[ParseItem::ITEM_PREDICATE_QUERY] = 'P';
        _name[ParseItem::ITEM_REGEXP] = '^';
    }
    char operator[] (ParseItem::ItemType i) const { return _name[i]; }
    char operator[] (size_t i) const { return _name[i]; }
private:
    char _name[ParseItem::ITEM_MAX];
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
        type = ParseItem::GetType(rawtype);
        if (ParseItem::GetFeature_Weight(rawtype)) {
            int64_t tmpLong(0);
            p += vespalib::compress::Integer::decompress(tmpLong, p);
            metaStr.append("(w:");
            metaStr.append(make_string("%ld", tmpLong));
            metaStr.append(")");
        }
        if (ParseItem::getFeature_UniqueId(rawtype)) {
            p += vespalib::compress::Integer::decompressPositive(tmp, p);
            metaStr.append("(u:");
            metaStr.append(make_string("%ld", tmp));
            metaStr.append(")");
        }
        if (ParseItem::getFeature_Flags(rawtype)) {
            flags = *p++;
            metaStr.append("(f:");
            metaStr.append(make_string("%d", flags));
            metaStr.append(")");
        }
        if (ParseItem::GetCreator(flags) != ParseItem::CREA_ORIG) {
            metaStr.append("(c:");
            metaStr.append(make_string("%d", ParseItem::GetCreator(flags)));
            metaStr.append(")");
        }

        metaStr.append('/');
        result.append(metaStr);

        switch (type) {
        case ParseItem::ITEM_OR:
        case ParseItem::ITEM_AND:
        case ParseItem::ITEM_EQUIV:
        case ParseItem::ITEM_NOT:
        case ParseItem::ITEM_RANK:
        case ParseItem::ITEM_ANY:
            p += vespalib::compress::Integer::decompressPositive(tmp, p);
            arity = tmp;
            result.append(make_string("%c/%d~", _G_ItemName[type], arity));
            break;
        case ParseItem::ITEM_WEAK_AND:
        case ParseItem::ITEM_NEAR:
        case ParseItem::ITEM_ONEAR:
            p += vespalib::compress::Integer::decompressPositive(tmp, p);
            arity = tmp;
            p += vespalib::compress::Integer::decompressPositive(tmp, p);
            arg1 = tmp;
            if (type == ParseItem::ITEM_WEAK_AND) {
                p += vespalib::compress::Integer::decompressPositive(tmp, p);
                idxRefLen = tmp;
                idxRef = p;
                p += idxRefLen;
                result.append(make_string("%c/%d/%d/%d:%.*s~", _G_ItemName[type], arity, arg1, idxRefLen, idxRefLen, idxRef));
            } else {
                result.append(make_string("%c/%d/%d~", _G_ItemName[type], arity, arg1));
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
            idxRefLen = tmp;
            idxRef = p;
            p += idxRefLen;
            p += vespalib::compress::Integer::decompressPositive(tmp, p);
            termRefLen = tmp;
            termRef = p;
            p += termRefLen;
            result.append(make_string("%c/%d:%.*s/%d:%.*s~", _G_ItemName[type],
                                            idxRefLen, idxRefLen, idxRef,
                                            termRefLen, termRefLen, termRef));
            break;
        case ParseItem::ITEM_PURE_WEIGHTED_STRING:
            p += vespalib::compress::Integer::decompressPositive(tmp, p);
            termRefLen = tmp;
            termRef = p;
            p += termRefLen;
            result.append(make_string("%c/%d:%.*s~", _G_ItemName[type],
                                            termRefLen, termRefLen, termRef));
            break;

        case ParseItem::ITEM_PURE_WEIGHTED_LONG:
            tmp = vespalib::nbo::n2h(*reinterpret_cast<const uint64_t *>(p));
            p += sizeof(uint64_t);
            result.append(make_string("%c/%lu", _G_ItemName[type], tmp));
            break;

        case ParseItem::ITEM_PHRASE:
        case ParseItem::ITEM_SAME_ELEMENT:
        case ParseItem::ITEM_WEIGHTED_SET:
        case ParseItem::ITEM_DOT_PRODUCT:
        case ParseItem::ITEM_WAND:
            p += vespalib::compress::Integer::decompressPositive(tmp, p);
            arity = tmp;
            p += vespalib::compress::Integer::decompressPositive(tmp, p);
            idxRefLen = tmp;
            idxRef = p;
            p += idxRefLen;
            if (type == ParseItem::ITEM_WAND) {
                p += vespalib::compress::Integer::decompressPositive(tmp, p);
                uint32_t targetNumHits = tmp;
                double scoreThreshold = vespalib::nbo::n2h(*reinterpret_cast<const double *>(p)); 
                p += sizeof(double);
                double thresholdBoostFactor = vespalib::nbo::n2h(*reinterpret_cast<const double *>(p)); // thresholdBoostFactor
                p += sizeof(double);
                result.append(make_string("%c/%d/%d:%.*s(%u,%f,%f)~", _G_ItemName[type], arity, idxRefLen,
                                                idxRefLen, idxRef, targetNumHits, scoreThreshold, thresholdBoostFactor));
            } else {
                result.append(make_string("%c/%d/%d:%.*s~", _G_ItemName[type], arity, idxRefLen,
                                                idxRefLen, idxRef));
            }
            break;

        case ParseItem::ITEM_PREDICATE_QUERY:
        {
            idxRefLen = static_cast<uint32_t>(ReadCompressedPositiveInt(p));
            idxRef = p;
            p += idxRefLen;
            size_t feature_count = ReadCompressedPositiveInt(p);
            result.append(make_string(
                    "%c/%d:%.*s/%zu(", _G_ItemName[type], idxRefLen, idxRefLen, idxRef, feature_count));
            for (size_t i = 0; i < feature_count; ++i) {
                vespalib::string key = ReadString(p);
                vespalib::string value = ReadString(p);
                uint64_t sub_queries = ReadUint64(p);
                result.append(make_string("%s:%s:%lx", key.c_str(), value.c_str(), sub_queries));
                if (i < feature_count - 1) {
                    result.append(',');
                }
            }

            size_t range_feature_count = ReadCompressedPositiveInt(p);
            result.append(make_string(")/%zu(", range_feature_count));
            for (size_t i = 0; i < range_feature_count; ++i) {
                vespalib::string key = ReadString(p);
                uint64_t value = ReadUint64(p);
                uint64_t sub_queries = ReadUint64(p);
                result.append(make_string("%s:%zu:%lx", key.c_str(), value, sub_queries));
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
