// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/* $Id$ */

#pragma once

#include "keyocc.h"
#include "querynode.h"

class Matcher;

/** This is the Juniper 1.0 version of MatchCandidate
 *  To be replaced by matchcand.{h,cpp}
 */

class MatchCandidate;

struct gtematch_cand {
    bool operator()(const MatchCandidate* m1, const MatchCandidate* m2) const;
    bool gtDistance(const MatchCandidate* m1, const MatchCandidate* m2) const;
};
typedef std::multiset<MatchCandidate*, gtematch_cand> match_candidate_set;

class MatchCandidate : public MatchElement
{
public:
    MatchElement** element;

    enum accept_state
    {
        M_OK,
        M_EXISTS,
        M_OVERLAP,
        M_EXPIRED,
        M_MAX
    };
private:
    QueryExpr* _match;
    int _nelems, _elems;
    // _startpos in superclass
    off_t _endpos;
    off_t _endtoken;
    long _docid;
    off_t _ctxt_start;
    size_t _elem_weight; // Combination of #elements and their weight, normal weight ~ 100
    int _options;
    int _overlap; // Handle terms matching multiple elements in ordered (distinct) mode
    uint32_t _refcnt;  // reference count for this object

    MatchCandidate(MatchCandidate &);
    MatchCandidate &operator=(MatchCandidate &);

public:
    keylist _klist;

    MatchCandidate(QueryExpr* query, MatchElement** elms, off_t ctxt_start);
    ~MatchCandidate();
    void ref() { ++_refcnt; }
    uint32_t deref() { --_refcnt; return _refcnt; }
    void set_valid() override;
    void dump(std::string& s) override;

    int elems() const { return _nelems; }
    int elem_store_sz() const { return _elems; }
    int word_distance() const { return _elems ? _endtoken - _starttoken - (_elems - 1) : 0; }
    off_t ctxt_startpos() const { return _ctxt_start; }
    off_t endtoken() const override { return _endtoken; }
    off_t endpos() const override { return _endpos; }
    ssize_t size() const { return _endpos - _startpos; }
    bool order() const { return _options & X_ORDERED; }
    bool partial_ok() const { return !(_options & X_COMPLETE); }
    QueryExpr* match() { return _match; }
    int weight() const { return _elem_weight; }
    size_t word_length() const override { return _endtoken - _starttoken; }

    bool complete() override;
    int weight(MatchElement* me, QueryExpr* mexp);

    size_t length() const override { return _endpos - _startpos; }

    MatchCandidate* Complex() override { return this; }

    void add_to_keylist(keylist& kl) override;
    void make_keylist();

    // A simple ranking function for now: Make sure those matches with
    // more keywords present gets ranked higher even if distance is
    // higher.
    //
    // Equal distance matches with similar amount of words should be ranked
    // according to how early in document they are.
    //
    // Rank function criterias:
    //   1. number and weight of elements in match
    //   2. distance in bytes between the elements (excluding the elements itself)
    //   3. significans of elements wrt. query order - order preserval [TBD]
    //   4. position in document
    //
    // Note that for start positions > 64K, each 256 byte further out in document
    // equals a distance increase by 8 bytes.
    //
    // also note that (for Juniper 1.0.x)
    // a distance increase of 512 bytes yields the effect of having one less
    // element in the match.
    //
    // Note (Juniper 2.0.x)
    // A normal keyword weight is assumed to be 100, with accepted range from 0 to 100000
    // Typical absolute values of the rank metric then becomes higher in 2.0 than in 1.0.x
    // and this also boosts the effect of having more keywords with significant weights
    // relative to the distance: a weight increase of 1 point on a single term now
    // equals a 16-byte distance, while a 100 byte weight increase (typical term addition)
    // equals 1600 bytes of distance increase.
    //
    inline int rank() const
    {
#ifdef JUNIPER_1_0_RANK
        // Just kept this here for reference..
        return (_nelems << 14) - ((_distance & ~0x7) << 5) - (_startpos >> 8);
#else
        return (_elem_weight << 11) - (word_distance() << 8) - (_startpos >> 8);
#endif
    }

    accept_state accept(MatchElement* k, QueryExpr* match);

    // Check optional WITHIN(limit) constraints:
    bool matches_limit();
    void log(std::string& logobj);
    void SetDocid(long id) { _docid = id; }
};

