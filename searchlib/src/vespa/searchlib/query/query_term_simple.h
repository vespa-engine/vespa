// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "query_normalization.h"
#include <vespa/vespalib/objects/objectvisitor.h>
#include <vespa/vespalib/util/memory.h>
#include <string>

namespace search {

/**
 * Basic representation of a query term.
 */
class QueryTermSimple {
public:
    using UP = std::unique_ptr<QueryTermSimple>;
    using string = std::string;
    using string_view = std::string_view;
    using Type = TermType;

    struct NumericRange {
        /* what we really want:
        double           fpLowerLimit = 0;
        double           fpUpperLimit = 0;
        int64_t          integerLowerLimit = 0;
        int64_t          integerUpperLimit = 0;
        */
        std::string_view lowerLimitTxt = "";
        std::string_view upperLimitTxt = "";
        bool             lower_inclusive = false;
        bool             upper_inclusive = false;
        bool             diversityCutoffStrict = false;
        std::string_view diversityAttribute = "";
        int32_t          rangeLimit = 0;
        uint32_t         maxPerGroup = 0;
        uint32_t         diversityCutoffGroups = std::numeric_limits<uint32_t>::max();

        bool has_lower_limit() const noexcept { return ! lowerLimitTxt.empty(); }
        bool has_upper_limit() const noexcept { return ! upperLimitTxt.empty(); }
        bool empty() const noexcept {
            return lowerLimitTxt.empty() && upperLimitTxt.empty();
        }
        bool just_one_value() const noexcept {
            return (lowerLimitTxt == upperLimitTxt) && lower_inclusive && upper_inclusive && has_lower_limit();
        }
        bool has_range_limit() const noexcept { return rangeLimit != 0; }
        bool with_diversity() const noexcept { return ! diversityAttribute.empty(); }
        bool with_diversity_cutoff() {
            return diversityCutoffGroups != std::numeric_limits<uint32_t>::max();
        }
    };

    template <typename N>
    struct RangeResult {
        N low;
        N high;
        bool valid; // Whether parsing of the range was successful
        bool adjusted; // Whether the low and high was adjusted according to min and max limits of the given type.
        RangeResult() noexcept : low(), high(), valid(true), adjusted(false) {}
        bool isEqual() const noexcept { return low == high; }
    };

    QueryTermSimple(const QueryTermSimple &) = delete;
    QueryTermSimple & operator = (const QueryTermSimple &) = delete;
    QueryTermSimple(QueryTermSimple &&) = delete;
    QueryTermSimple & operator = (QueryTermSimple &&) = delete;
    QueryTermSimple(const string & term_, Type type);
    QueryTermSimple(Type type, std::unique_ptr<NumericRange> range);
    virtual ~QueryTermSimple();


    /**
     * Extracts the content of this query term as a range with low and high values.
     */
    template <typename N>
    RangeResult<N> getRange() const noexcept;
    int                         getRangeLimit() const noexcept { return activeRange().rangeLimit; }
    size_t                     getMaxPerGroup() const noexcept { return activeRange().maxPerGroup; }
    size_t           getDiversityCutoffGroups() const noexcept { return activeRange().diversityCutoffGroups; }
    bool             getDiversityCutoffStrict() const noexcept { return activeRange().diversityCutoffStrict; }
    string_view         getDiversityAttribute() const noexcept { return activeRange().diversityAttribute; }
    [[nodiscard]] size_t fuzzy_max_edit_distance() const noexcept { return _fuzzy_max_edit_distance; }
    [[nodiscard]] size_t fuzzy_prefix_lock_length() const noexcept { return _fuzzy_prefix_lock_length; }
    [[nodiscard]] bool   fuzzy_prefix_match() const noexcept { return _fuzzy_prefix_match; }
    bool getAsIntegerTerm(int64_t & lower, int64_t & upper) const noexcept;
    bool getAsFloatTerm(double & lower, double & upper) const noexcept;
    bool getAsFloatTerm(float & lower, float & upper) const noexcept;
    const char * getTerm() const noexcept { return _term.c_str(); }
    bool isPrefix()        const noexcept { return (_type == Type::PREFIXTERM); }
    bool isSubstring()     const noexcept { return (_type == Type::SUBSTRINGTERM); }
    bool isExactstring()   const noexcept { return (_type == Type::EXACTSTRINGTERM); }
    bool isSuffix()        const noexcept { return (_type == Type::SUFFIXTERM); }
    bool isWord()          const noexcept { return (_type == Type::WORD); }
    bool isRegex()         const noexcept { return (_type == Type::REGEXP); }
    bool isGeoLoc()        const noexcept { return (_type == Type::GEO_LOCATION); }
    bool isFuzzy()         const noexcept { return (_type == Type::FUZZYTERM); }
    bool is_nearest_neighbor() const noexcept { return (_type == Type::NEAREST_NEIGHBOR); }
    bool empty()           const noexcept { return _term.empty(); }
    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const;
    string getClassName() const;
    bool isValid() const noexcept { return _valid; }
    const string & getTermString() const noexcept { return _term; }

private:
    static NumericRange emptyNumericRange;
    std::unique_ptr<NumericRange> _numeric_range = {};
    bool getRangeInternal(int64_t & low, int64_t & high) const noexcept;
    template <typename N>
    RangeResult<N> getIntegerRange() const noexcept;
    template <typename N>
    RangeResult<N>    getFloatRange() const noexcept;
    Type        _type;
    bool        _valid;
protected:
    bool        _fuzzy_prefix_match; // set in QueryTerm
private:
    string      _term;
    template <typename T, typename D>
    bool    getAsNumericTerm(T & lower, T & upper, D d) const noexcept;
    const NumericRange& activeRange() const noexcept { return _numeric_range ? (*_numeric_range) : emptyNumericRange; }

protected:
    uint32_t    _fuzzy_max_edit_distance;  // set in QueryTerm
    uint32_t    _fuzzy_prefix_lock_length;
};

}

void visit(vespalib::ObjectVisitor &self, const std::string &name,
           const search::QueryTermSimple &obj);
void visit(vespalib::ObjectVisitor &self, const std::string &name,
           const search::QueryTermSimple *obj);
