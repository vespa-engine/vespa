// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/objects/objectvisitor.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/memory.h>

namespace search {

/**
 * Basic representation of a query term.
 */
class QueryTermSimple {
public:
    using UP = std::unique_ptr<QueryTermSimple>;
    using string = vespalib::string;
    using stringref = vespalib::stringref;
    enum class Type : uint8_t {
        WORD = 0,
        PREFIXTERM = 1,
        SUBSTRINGTERM = 2,
        EXACTSTRINGTERM = 3,
        SUFFIXTERM = 4,
        REGEXP = 5,
        GEO_LOCATION = 6,
        FUZZYTERM = 7,
        NEAREST_NEIGHBOR = 8
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

    QueryTermSimple(const QueryTermSimple &) = delete;
    QueryTermSimple & operator = (const QueryTermSimple &) = delete;
    QueryTermSimple(QueryTermSimple &&) = delete;
    QueryTermSimple & operator = (QueryTermSimple &&) = delete;
    QueryTermSimple(const string & term_, Type type);
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
    size_t           getFuzzyMaxEditDistance() const { return _fuzzyMaxEditDistance; }
    size_t           getFuzzyPrefixLength() const { return _fuzzyPrefixLength; }
    bool getAsIntegerTerm(int64_t & lower, int64_t & upper) const;
    bool getAsDoubleTerm(double & lower, double & upper) const;
    const char * getTerm() const { return _term.c_str(); }
    bool isPrefix()        const { return (_type == Type::PREFIXTERM); }
    bool isSubstring()     const { return (_type == Type::SUBSTRINGTERM); }
    bool isExactstring()   const { return (_type == Type::EXACTSTRINGTERM); }
    bool isSuffix()        const { return (_type == Type::SUFFIXTERM); }
    bool isWord()          const { return (_type == Type::WORD); }
    bool isRegex()         const { return (_type == Type::REGEXP); }
    bool isGeoLoc()        const { return (_type == Type::GEO_LOCATION); }
    bool isFuzzy()         const { return (_type == Type::FUZZYTERM); }
    bool is_nearest_neighbor() const noexcept { return (_type == Type::NEAREST_NEIGHBOR); }
    bool empty()           const { return _term.empty(); }
    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const;
    vespalib::string getClassName() const;
    bool isValid() const { return _valid; }
    const string & getTermString() const { return _term; }

private:
    bool getRangeInternal(int64_t & low, int64_t & high) const;
    template <typename N>
    RangeResult<N> getIntegerRange() const;
    template <typename N>
    RangeResult<N>    getFloatRange() const;
    int         _rangeLimit;
    uint32_t    _maxPerGroup;
    uint32_t    _diversityCutoffGroups;
    Type        _type;
    bool        _diversityCutoffStrict;
    bool        _valid;
    string      _term;
    stringref   _diversityAttribute;
    template <typename T, typename D>
    bool    getAsNumericTerm(T & lower, T & upper, D d) const;

protected:
    uint32_t    _fuzzyMaxEditDistance;  // set in QueryTerm
    uint32_t    _fuzzyPrefixLength;
};

}

void visit(vespalib::ObjectVisitor &self, const vespalib::string &name,
           const search::QueryTermSimple &obj);
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name,
           const search::QueryTermSimple *obj);

