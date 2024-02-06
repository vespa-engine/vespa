// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "queryterm.h"
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/vespalib/objects/visit.h>
#include <algorithm>
#include <limits>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.query.streaming.queryterm");

using search::fef::ITermData;
using search::fef::ITermFieldData;
using search::fef::MatchData;
using search::fef::TermFieldMatchData;
using search::fef::TermFieldMatchDataPosition;

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

QueryTerm::QueryTerm(std::unique_ptr<QueryNodeResultBase> org, stringref termS, const string & indexS,
                     Type type, Normalizing normalizing)
    : QueryTermUCS4(QueryNormalization::optional_fold(termS, type, normalizing), type),
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

uint32_t
QueryTerm::add(uint32_t field_id, uint32_t element_id, int32_t element_weight, uint32_t position)
{
    uint32_t idx = _hitList.size();
    _hitList.emplace_back(field_id, element_id, element_weight, position);
    return idx;
}

void
QueryTerm::set_element_length(uint32_t hitlist_idx, uint32_t element_length)
{
    _hitList[hitlist_idx].set_element_length(element_length);
}

namespace {

uint16_t
cap_16_bits(uint32_t value)
{
    return std::min(value, static_cast<uint32_t>(std::numeric_limits<uint16_t>::max()));
}

uint32_t
extract_field_length(const QueryTerm& term, uint32_t field_id)
{
    return (field_id < term.getFieldInfoSize()) ? term.getFieldInfo(field_id).getFieldLength() : search::fef::FieldPositionsIterator::UNKNOWN_LENGTH;
}

void
set_interleaved_features(TermFieldMatchData& tmd, uint32_t field_length, uint32_t num_occs)
{
    tmd.setFieldLength(cap_16_bits(field_length));
    tmd.setNumOccs(cap_16_bits(num_occs));
}

}

void
QueryTerm::unpack_match_data_helper(uint32_t docid, const fef::ITermData& td, fef::MatchData& match_data, const QueryTerm& fl_term) const
{
    HitList list;
    const HitList & hitList = evaluateHits(list);

    if (!hitList.empty()) { // only unpack if we have a hit
        LOG(debug, "Unpack match data for query term '%s:%s'",
            index().c_str(), getTerm());

        uint32_t lastFieldId = -1;
        TermFieldMatchData *tmd = nullptr;
        uint32_t num_occs = 0;

        // optimize for hitlist giving all hits for a single field in one chunk
        for (const Hit & hit : hitList) {
            uint32_t fieldId = hit.field_id();
            if (fieldId != lastFieldId) {
                if (tmd != nullptr) {
                    if (tmd->needs_interleaved_features()) {
                        set_interleaved_features(*tmd, extract_field_length(fl_term, lastFieldId), num_occs);
                    }
                    // reset to notfound/unknown values
                    tmd = nullptr;
                }
                num_occs = 0;

                // setup for new field that had a hit
                const ITermFieldData *tfd = td.lookupField(fieldId);
                if (tfd != nullptr) {
                    tmd = match_data.resolveTermField(tfd->getHandle());
                    tmd->setFieldId(fieldId);
                    // reset field match data, but only once per docId
                    if (tmd->getDocId() != docid) {
                        tmd->reset(docid);
                    }
                }
                lastFieldId = fieldId;
            }
            ++num_occs;
            if (tmd != nullptr) {
                TermFieldMatchDataPosition pos(hit.element_id(), hit.position(),
                                               hit.element_weight(), hit.element_length());
                tmd->appendPosition(pos);
                LOG(debug, "Append elemId(%u),position(%u), weight(%d), tfmd.weight(%d)",
                    pos.getElementId(), pos.getPosition(), pos.getElementWeight(), tmd->getWeight());
            }
        }
        if (tmd != nullptr) {
            if (tmd->needs_interleaved_features()) {
                set_interleaved_features(*tmd, extract_field_length(fl_term, lastFieldId), num_occs);
            }
        }
    }
}

void
QueryTerm::unpack_match_data(uint32_t docid, const fef::ITermData& td, fef::MatchData& match_data)
{
    unpack_match_data_helper(docid, td, match_data, *this);
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
