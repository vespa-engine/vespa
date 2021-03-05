// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "queryterm.h"
#include <vespa/vespalib/objects/visit.h>
#include <vespa/vespalib/text/utf8.h>
#include <cmath>

namespace {

class CharInfo {
public:
    CharInfo();
    uint8_t get(uint8_t c) const { return _charInfo[c]; }
private:
    uint8_t _charInfo[256];
};

CharInfo::CharInfo()
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

static CharInfo _G_charTable;

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

QueryTerm::QueryTerm(std::unique_ptr<QueryNodeResultBase> org, const string & termS, const string & indexS, Type type) :
    QueryTermUCS4(termS, type),
    _index(indexS),
    _encoding(0x01),
    _result(org.release()),
    _hitList(),
    _weight(100),
    _uniqueId(0),
    _fieldInfo()
{
    if (!termS.empty()) {
        uint8_t enc(0xff);
        for (size_t i(0), m(termS.size()); i < m; i++) {
            enc &= _G_charTable.get(termS[i]);
        }
        _encoding = enc;
    }
}

void QueryTerm::getPhrases(QueryNodeRefList & tl)            { (void) tl; }
void QueryTerm::getPhrases(ConstQueryNodeRefList & tl) const { (void) tl; }
void QueryTerm::getLeafs(QueryTermList & tl)                 { tl.push_back(this); }
void QueryTerm::getLeafs(ConstQueryTermList & tl)      const { tl.push_back(this); }
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

}
