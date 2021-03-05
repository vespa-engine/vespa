// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "hit.h"
#include "querynode.h"
#include "querynoderesultbase.h"
#include <vespa/fastlib/text/unicodeutil.h>
#include <vespa/searchlib/query/query_term_ucs4.h>
#include <vespa/searchlib/query/weight.h>
#include <vespa/vespalib/objects/objectvisitor.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/memory.h>

namespace search::streaming {

/**
   This is a leaf in the Query tree. All terms are leafs.
   A QueryTerm has the index for where to find the term. The term is a string,
   both char(utf8) and ucs4. There are flags indicating encoding. And there are
   flags indicating if it should be considered a prefix.
*/
class QueryTerm : public QueryTermUCS4, public QueryNode
{
public:
    typedef std::unique_ptr<QueryTerm> UP;
    class EncodingBitMap
    {
    public:
        EncodingBitMap(uint8_t bm=0) : _enc(bm) { }
        bool isFloat()        const { return _enc & Float; }
        bool isBase10Integer()        const { return _enc & Base10Integer; }
        bool isAscii7Bit()            const { return _enc & Ascii7Bit; }
        void setBase10Integer(bool v)       { if (v) _enc |= Base10Integer; else _enc &= ~Base10Integer; }
        void setAscii7Bit(bool v)           { if (v) _enc |= Ascii7Bit; else _enc &= ~Ascii7Bit; }
        void setFloat(bool v)               { if (v) _enc |= Float; else _enc &= ~Float; }
    private:
        enum { Ascii7Bit=0x01, Base10Integer=0x02, Float=0x04 };
        uint8_t _enc;
    };
    class FieldInfo {
    public:
        FieldInfo() : _hitListOffset(0), _hitCount(0), _fieldLength(0) { }
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
    QueryTerm(std::unique_ptr<QueryNodeResultBase> resultBase, const string & term, const string & index, Type type);
    QueryTerm(const QueryTerm &) = delete;
    QueryTerm & operator = (const QueryTerm &) = delete;
    QueryTerm(QueryTerm &&) = delete;
    QueryTerm & operator = (QueryTerm &&) = delete;
    ~QueryTerm();
    bool evaluate() const override;
    const HitList & evaluateHits(HitList & hl) const override;
    void reset() override;
    void getLeafs(QueryTermList & tl) override;
    void getLeafs(ConstQueryTermList & tl) const override;
    /// Gives you all phrases of this tree.
    void getPhrases(QueryNodeRefList & tl) override;
    /// Gives you all phrases of this tree. Indicating that they are all const.
    void getPhrases(ConstQueryNodeRefList & tl) const override;

    void                add(unsigned pos, unsigned context, uint32_t elemId, int32_t weight);
    EncodingBitMap      encoding()                 const { return _encoding; }
    size_t              termLen()                  const { return getTermLen(); }
    const string      & index()                    const { return _index; }
    void                setWeight(query::Weight v)       { _weight = v; }
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
protected:
    using QueryNodeResultBaseContainer = vespalib::CloneablePtr<QueryNodeResultBase>;
    string                       _index;
    EncodingBitMap               _encoding;
    QueryNodeResultBaseContainer _result;
    HitList                      _hitList;
private:
    query::Weight                _weight;
    uint32_t                     _uniqueId;
    std::vector<FieldInfo>       _fieldInfo;
};

}


