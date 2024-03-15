// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "hit.h"
#include "querynode.h"
#include "querynoderesultbase.h"
#include <vespa/searchlib/query/query_term_ucs4.h>
#include <vespa/searchlib/query/weight.h>
#include <vespa/vespalib/objects/objectvisitor.h>
#include <vespa/vespalib/stllike/string.h>

namespace search::fef {

class IIndexEnvironment;
class ITermData;
class MatchData;

}
namespace search::streaming {

class EquivQueryNode;
class FuzzyTerm;
class NearestNeighborQueryNode;
class MultiTerm;
class RegexpTerm;

/**
   This is a leaf in the Query tree. All terms are leafs.
   A QueryTerm has the index for where to find the term. The term is a string,
   both char(utf8) and ucs4. There are flags indicating encoding. And there are
   flags indicating if it should be considered a prefix.
*/
class QueryTerm : public QueryTermUCS4, public QueryNode
{
public:
    using UP = std::unique_ptr<QueryTerm>;
    class EncodingBitMap
    {
    public:
        explicit EncodingBitMap(uint8_t bm) : _enc(bm) { }
        bool isFloat()        const { return _enc & Float; }
        bool isBase10Integer()        const { return _enc & Base10Integer; }
        bool isAscii7Bit()            const { return _enc & Ascii7Bit; }
    private:
        enum { Ascii7Bit=0x01, Base10Integer=0x02, Float=0x04 };
        uint8_t _enc;
    };
    class FieldInfo {
    public:
        FieldInfo() noexcept : _hitListOffset(0), _hitCount(0), _fieldLength(0) { }
        FieldInfo(uint32_t hitListOffset, uint32_t hitCount, uint32_t fieldLength) :
            _hitListOffset(hitListOffset), _hitCount(hitCount), _fieldLength(fieldLength) { }
        size_t getHitOffset()     const { return _hitListOffset; }
        size_t getHitCount()      const { return _hitCount; }
        size_t getFieldLength()   const { return _fieldLength; }
        FieldInfo & setHitOffset(size_t v)   { _hitListOffset = v; return *this; }
        FieldInfo & setHitCount(size_t v)    { _hitCount = v; return *this; }
        FieldInfo & setFieldLength(size_t v) { _fieldLength = v; return *this; }
    private:
        uint32_t _hitListOffset;
        uint32_t _hitCount;
        uint32_t _fieldLength;
    };
    QueryTerm(std::unique_ptr<QueryNodeResultBase> resultBase, stringref term, const string & index, Type type)
        : QueryTerm(std::move(resultBase), term, index, type, (type == Type::EXACTSTRINGTERM)
                                                              ? Normalizing::LOWERCASE
                                                              : Normalizing::LOWERCASE_AND_FOLD)
    {}
    QueryTerm(std::unique_ptr<QueryNodeResultBase> resultBase, stringref term, const string & index, Type type, Normalizing normalizing);
    QueryTerm(const QueryTerm &) = delete;
    QueryTerm & operator = (const QueryTerm &) = delete;
    QueryTerm(QueryTerm &&) = delete;
    QueryTerm & operator = (QueryTerm &&) = delete;
    ~QueryTerm() override;
    bool evaluate() const override;
    const HitList & evaluateHits(HitList & hl) const override;
    void reset() override;
    void getLeaves(QueryTermList & tl) override;
    void getLeaves(ConstQueryTermList & tl) const override;

    uint32_t            add(uint32_t field_id, uint32_t element_id, int32_t element_weight, uint32_t position);
    void                set_element_length(uint32_t hitlist_idx, uint32_t element_length);
    EncodingBitMap      encoding()                 const { return _encoding; }
    size_t              termLen()                  const { return getTermLen(); }
    const string      & index()                    const { return _index; }
    void                setWeight(query::Weight v)       { _weight = v; }
    void                setRanked(bool ranked)           { _isRanked = ranked; }
    bool                isRanked()                 const { return _isRanked; }
    void                set_filter(bool v) noexcept      { _filter = v; }
    bool                is_filter() const noexcept       { return _filter; }
    void                setUniqueId(uint32_t u)          { _uniqueId = u; }
    query::Weight       weight()                   const { return _weight; }
    uint32_t            uniqueId()                 const { return _uniqueId; }
    void                resizeFieldId(size_t fieldId);
    const FieldInfo   & getFieldInfo(size_t fid)   const { return _fieldInfo[fid]; }
    FieldInfo         & getFieldInfo(size_t fid)         { return _fieldInfo[fid]; }
    size_t              getFieldInfoSize()         const { return _fieldInfo.size(); }
    QueryNodeResultBase & getQueryItem() { return *_result; }
    const HitList &     getHitList() const { return _hitList; }
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    void setIndex(const string & index_) override { _index = index_; }
    const string & getIndex() const override { return _index; }
    void setFuzzyMaxEditDistance(uint32_t fuzzyMaxEditDistance) { _fuzzyMaxEditDistance = fuzzyMaxEditDistance; }
    void setFuzzyPrefixLength(uint32_t fuzzyPrefixLength) { _fuzzyPrefixLength = fuzzyPrefixLength; }
    virtual NearestNeighborQueryNode* as_nearest_neighbor_query_node() noexcept;
    virtual MultiTerm* as_multi_term() noexcept;
    virtual RegexpTerm* as_regexp_term() noexcept;
    virtual FuzzyTerm* as_fuzzy_term() noexcept;
    virtual EquivQueryNode* as_equiv_query_node() noexcept;
    virtual const EquivQueryNode* as_equiv_query_node() const noexcept;
    virtual void unpack_match_data(uint32_t docid, const fef::ITermData& td, fef::MatchData& match_data, const fef::IIndexEnvironment& index_env);
protected:
    template <typename HitListType>
    static void unpack_match_data_helper(uint32_t docid, const fef::ITermData& td, fef::MatchData& match_data, const HitListType& hit_list, const QueryTerm& fl_term, bool term_filter, const fef::IIndexEnvironment& index_env);
    using QueryNodeResultBaseContainer = std::unique_ptr<QueryNodeResultBase>;
    HitList                      _hitList;
private:
    string                       _index;
    QueryNodeResultBaseContainer _result;
    EncodingBitMap               _encoding;
    bool                         _isRanked;
    bool                         _filter;
    query::Weight                _weight;
    uint32_t                     _uniqueId;
    std::vector<FieldInfo>       _fieldInfo;
};

}


