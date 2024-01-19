// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "queryterm.h"
#include <vespa/fastlib/text/normwordfolder.h>
#include <vespa/vespalib/objects/visit.h>
#include <cmath>

namespace {

class CharInfo {
public:
    CharInfo();
    uint8_t get(uint8_t c) const noexcept { return _charInfo[c]; }
private:
    uint8_t _charInfo[256];
};

CharInfo::CharInfo()
    : _charInfo()
{
    // XXX: Should refactor to reduce number of magic constants.
    memset(_charInfo, 0x01, 128); // All 7 bits are ascii7bit
    memset(_charInfo+128, 0x00, 128); // The rest are not.
    memset(_charInfo + static_cast<uint8_t>('0'), 0x07, 10);
    _charInfo[uint8_t('-')] = 0x07;
    _charInfo[uint8_t('<')] = 0x07;
    _charInfo[uint8_t('>')] = 0x07;
    _charInfo[uint8_t(';')] = 0x07;
    _charInfo[uint8_t('[')] = 0x07;
    _charInfo[uint8_t(']')] = 0x07;

    _charInfo[uint8_t('.')] = 0x05;
    _charInfo[uint8_t('+')] = 0x05;
    _charInfo[uint8_t('e')] = 0x05;
    _charInfo[uint8_t('E')] = 0x05;
}

CharInfo G_charTable;

}

namespace search::streaming {

QueryTerm::~QueryTerm() = default;

void
QueryTerm::visitMembers(vespalib::ObjectVisitor & visitor) const
{
    QueryTermUCS4::visitMembers(visitor);
    visit(visitor, "encoding.isBase10Integer", _encoding.isBase10Integer());
    visit(visitor, "encoding.isFloat", _encoding.isFloat());
    visit(visitor, "encoding.isAscii7Bit", _encoding.isAscii7Bit());
    visit(visitor, "index", _index);
    visit(visitor, "weight", _weight.percent());
    visit(visitor, "uniqueid", _uniqueId);
}

namespace {

using Type = QueryTermSimple::Type;

Normalizing
requireFold(Type type, Normalizing normalizing) {
    if (normalizing == Normalizing::NONE) return Normalizing::NONE;
    if (normalizing == Normalizing::LOWERCASE) return Normalizing::LOWERCASE;
    if (type == Type::EXACTSTRINGTERM) return Normalizing::LOWERCASE;
    return ((type == Type::WORD) || (type == Type::SUBSTRINGTERM) ||
           (type == Type::PREFIXTERM) || (type == Type::SUFFIXTERM))
           ? Normalizing::LOWERCASE_AND_FOLD
           : Normalizing::NONE;
}

vespalib::string
fold(vespalib::stringref s) {
    const auto * curr = reinterpret_cast<const unsigned char *>(s.data());
    const unsigned char * end = curr + s.size();
    vespalib::string folded;
    for (; curr < end;) {
        uint32_t c_ucs4 = *curr;
        if (c_ucs4 < 0x80) {
            folded.append(Fast_NormalizeWordFolder::lowercase_and_fold_ascii(*curr++));
        } else {
            c_ucs4 = Fast_UnicodeUtil::GetUTF8CharNonAscii(curr);
            const char *repl = Fast_NormalizeWordFolder::ReplacementString(c_ucs4);
            if (repl != nullptr) {
                size_t repllen = strlen(repl);
                folded.append(repl, repllen);
            } else {
                c_ucs4 = Fast_NormalizeWordFolder::lowercase_and_fold(c_ucs4);
                char tmp[6];
                const char * tmp_end = Fast_UnicodeUtil::utf8cput(tmp, c_ucs4);
                folded.append(tmp, tmp_end - tmp);
            }
        }
    }
    return folded;
}

vespalib::string
lowercase(vespalib::stringref s) {
    const auto * curr = reinterpret_cast<const unsigned char *>(s.data());
    const unsigned char * end = curr + s.size();
    vespalib::string folded;
    for (; curr < end;) {
        uint32_t c_ucs4 = *curr;
        if (c_ucs4 < 0x80) {
            folded.append(static_cast<char>(Fast_NormalizeWordFolder::lowercase_ascii(*curr++)));
        } else {
            c_ucs4 = Fast_NormalizeWordFolder::lowercase(Fast_UnicodeUtil::GetUTF8CharNonAscii(curr));
            char tmp[6];
            const char * tmp_end = Fast_UnicodeUtil::utf8cput(tmp, c_ucs4);
            folded.append(tmp, tmp_end - tmp);
        }
    }
    return folded;
}

vespalib::string
optional_fold(vespalib::stringref s, Type type, Normalizing normalizing) {
    switch ( requireFold(type, normalizing)) {
        case Normalizing::NONE: return s;
        case Normalizing::LOWERCASE: return lowercase(s);
        case Normalizing::LOWERCASE_AND_FOLD: return fold(s);
    }
    return s;
}

}

QueryTerm::QueryTerm(std::unique_ptr<QueryNodeResultBase> org, stringref termS, const string & indexS,
                     Type type, Normalizing normalizing)
    : QueryTermUCS4(optional_fold(termS, type, normalizing), type),
      _index(indexS),
      _encoding(0x01),
      _result(org.release()),
      _hitList(),
      _weight(100),
      _uniqueId(0),
      _fieldInfo()
{
    if (!empty()) {
        uint8_t enc(0xff);
        for (char c : getTermString()) {
            enc &= G_charTable.get(c);
        }
        _encoding = EncodingBitMap(enc);
    }
}

void QueryTerm::getPhrases(QueryNodeRefList & tl)            { (void) tl; }
void QueryTerm::getPhrases(ConstQueryNodeRefList & tl) const { (void) tl; }
void QueryTerm::getLeaves(QueryTermList & tl)                { tl.push_back(this); }
void QueryTerm::getLeaves(ConstQueryTermList & tl)     const { tl.push_back(this); }
bool QueryTerm::evaluate()                             const { return !_hitList.empty(); }
void QueryTerm::reset()                                      { _hitList.clear(); }
const HitList & QueryTerm::evaluateHits(HitList &) const { return _hitList; }

void QueryTerm::resizeFieldId(size_t fieldNo)
{
    if (fieldNo >= _fieldInfo.size()) {
        _fieldInfo.resize(std::max(32ul, fieldNo + 1));
    }
}

void QueryTerm::add(unsigned pos, unsigned context, uint32_t elemId, int32_t weight_)
{
    _hitList.emplace_back(pos, context, elemId, weight_);
}

NearestNeighborQueryNode*
QueryTerm::as_nearest_neighbor_query_node() noexcept
{
    return nullptr;
}

MultiTerm*
QueryTerm::as_multi_term() noexcept
{
    return nullptr;
}

RegexpTerm*
QueryTerm::as_regexp_term() noexcept
{
    return nullptr;
}

FuzzyTerm*
QueryTerm::as_fuzzy_term() noexcept
{
    return nullptr;
}

}
