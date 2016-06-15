// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/* $Id$ */

#pragma once

#include "keyocc.h"
#include <string>
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
typedef JUNIPER_MULTISET<MatchCandidate*, gtematch_cand> match_candidate_set;

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
    virtual ~MatchCandidate();
    void ref() { ++_refcnt; }
    uint32_t deref() { --_refcnt; return _refcnt; }
    virtual void set_valid();
    virtual void dump(std::string& s);

    inline int elems() const { return _nelems; }
    inline int elem_store_sz() const { return _elems; }
    inline int word_distance() const { return _elems ? _endtoken - _starttoken - (_elems - 1) : 0; }
    inline off_t ctxt_startpos() const { return _ctxt_start; }
    virtual inline off_t endtoken() const { return _endtoken; }
    virtual inline off_t endpos() const { return _endpos; }
    inline ssize_t size() const { return _endpos - _startpos; }
    inline bool order() const { return _options & X_ORDERED; }
    inline bool partial_ok() const { return !(_options & X_COMPLETE); }
    inline QueryExpr* match() { return _match; }
    inline int weight() const { return _elem_weight; }
    inline size_t word_length() const { return _endtoken - _starttoken; }

    virtual bool complete();
    int weight(MatchElement* me, QueryExpr* mexp);

    virtual size_t length() const { return _endpos - _startpos; }

    virtual MatchCandidate* Complex() { return this; }

    virtual void add_to_keylist(keylist& kl);
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

