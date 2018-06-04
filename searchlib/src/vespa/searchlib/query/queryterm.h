// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "posocc.h"
#include "querynode.h"
#include "querynoderesultbase.h"
#include "weight.h"
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/memory.h>
#include <vespa/vespalib/objects/objectvisitor.h>
#include <vespa/fastlib/text/unicodeutil.h>

namespace search {


class QueryTermSimple
{
public:
    typedef std::unique_ptr<QueryTermSimple> UP;
    typedef vespalib::string string;
    typedef vespalib::stringref stringref;
    enum SearchTerm {
        WORD,
        PREFIXTERM,
        SUBSTRINGTERM,
        EXACTSTRINGTERM,
        SUFFIXTERM,
        REGEXP
    };

    template <typename N>
    struct RangeResult {
        N low;
        N high;
        bool valid; // Whether parsing of the range was successful
        bool adjusted; // Whether the low and high was adjusted according to min and max limits of the given type.
        RangeResult() : low(), high(), valid(true), adjusted(false) {}
        bool isEqual() const { return low == high; }
    };

    QueryTermSimple(const QueryTermSimple &) = default;
    QueryTermSimple & operator = (const QueryTermSimple &) = default;
    QueryTermSimple(QueryTermSimple &&) = default;
    QueryTermSimple & operator = (QueryTermSimple &&) = default;
    QueryTermSimple();
    QueryTermSimple(const string & term_, SearchTerm type);
    virtual ~QueryTermSimple();
    /**
     * Extracts the content of this query term as a range with low and high values.
     */
    template <typename N>
    RangeResult<N> getRange() const;
    int                         getRangeLimit() const { return _rangeLimit; }
    size_t                     getMaxPerGroup() const { return _maxPerGroup; }
    size_t           getDiversityCutoffGroups() const { return _diversityCutoffGroups; }
    bool             getDiversityCutoffStrict() const { return _diversityCutoffStrict; }
    vespalib::stringref getDiversityAttribute() const { return _diversityAttribute; }
    bool getAsIntegerTerm(int64_t & lower, int64_t & upper) const;
    bool getAsDoubleTerm(double & lower, double & upper) const;
    const char * getTerm() const { return _term.c_str(); }
    bool isPrefix()        const { return (_type == PREFIXTERM); }
    bool isSubstring()     const { return (_type == SUBSTRINGTERM); }
    bool isExactstring()   const { return (_type == EXACTSTRINGTERM); }
    bool isSuffix()        const { return (_type == SUFFIXTERM); }
    bool isWord()          const { return (_type == WORD); }
    bool isRegex()         const { return (_type == REGEXP); }
    bool empty()           const { return _term.empty(); }
    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const;
    vespalib::string getClassName() const;
    bool isValid() const { return _valid; }
protected:
    const string & getTermString() const { return _term; }
private:
    bool getRangeInternal(int64_t & low, int64_t & high) const;
    template <typename N>
    RangeResult<N> getIntegerRange() const;
    template <typename N>
    RangeResult<N>    getFloatRange() const;
    SearchTerm  _type;
    int         _rangeLimit;
    uint32_t    _maxPerGroup;
    uint32_t    _diversityCutoffGroups;
    bool        _diversityCutoffStrict;
    bool        _valid;
    string      _term;
    stringref   _diversityAttribute;
    template <typename T, typename D>
    bool    getAsNumericTerm(T & lower, T & upper, D d) const;
};

class QueryTermBase : public QueryTermSimple
{
public:
    typedef std::vector<ucs4_t> UCS4StringT;
    typedef std::unique_ptr<QueryTermBase> UP;
    QueryTermBase(const QueryTermBase &) = default;
    QueryTermBase & operator = (const QueryTermBase &) = default;
    QueryTermBase(QueryTermBase &&) = default;
    QueryTermBase & operator = (QueryTermBase &&) = default;
    QueryTermBase();
    QueryTermBase(const string & term_, SearchTerm type);
    ~QueryTermBase();
    size_t getTermLen() const { return _cachedTermLen; }
    size_t term(const char * & t)     const { t = getTerm(); return _cachedTermLen; }
    UCS4StringT getUCS4Term() const;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    size_t term(const ucs4_t * & t) {
        if (_termUCS4.empty()) {
            _termUCS4 = getUCS4Term();
        }
        t = &_termUCS4[0];
        return _cachedTermLen;
    }
private:
    size_t                       _cachedTermLen;
    UCS4StringT                  _termUCS4;
};

/**
   This is a leaf in the Query tree. All terms are leafs.
   A QueryTerm has the index for where to find the term. The term is a string,
   both char(utf8) and ucs4. There are flags indicating encoding. And there are
   flags indicating if it should be considered a prefix.
*/
class QueryTerm : public QueryTermBase, public QueryNode
{
public:
    typedef std::unique_ptr<QueryTerm> UP;
    class EncodingBitMap
    {
    public:
        EncodingBitMap(unsigned bm=0) : _enc(bm) { }
        bool isFloat()        const { return _enc & Float; }
        bool isBase10Integer()        const { return _enc & Base10Integer; }
        bool isAscii7Bit()            const { return _enc & Ascii7Bit; }
        void setBase10Integer(bool v)       { if (v) _enc |= Base10Integer; else _enc &= ~Base10Integer; }
        void setAscii7Bit(bool v)           { if (v) _enc |= Ascii7Bit; else _enc &= ~Ascii7Bit; }
        void setFloat(bool v)               { if (v) _enc |= Float; else _enc &= ~Float; }
    private:
        enum { Ascii7Bit=0x01, Base10Integer=0x02, Float=0x04 };
        unsigned _enc;
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
    QueryTerm();
    QueryTerm(std::unique_ptr<QueryNodeResultBase> resultBase, const string & term, const string & index, SearchTerm type);
    QueryTerm(const QueryTerm &);
    QueryTerm & operator = (const QueryTerm &);
    QueryTerm(QueryTerm &&);
    QueryTerm & operator = (QueryTerm &&);
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

void visit(vespalib::ObjectVisitor &self, const vespalib::string &name,
           const search::QueryTermSimple &obj);
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name,
           const search::QueryTermSimple *obj);

