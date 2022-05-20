// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/* $Id$ */

#pragma once

#include "keyocc.h"
#include "mcand.h"
#include "queryhandle.h"

// #define USE_OLD_SCANNER 1


#ifdef USE_OLD_SCANNER
#define TokenDispatcher DocScanner
#endif

#include <vector>
#include <list>
#include <map>
#include <string>
#include "ITokenProcessor.h"
#include "querynode.h"
#include "matchobject.h"
#include "querymodifier.h"

#ifdef __hpux__
// HP-UX does "magic" with max and min macros so algorithm must have been included
// before we attempt to define the macros..
#include <algorithm>
#endif

class MatchCandidate;

// Define this to get hash algorithm to do keyword comparisons
// O(1) wrt. number of query words
#define USE_HASHED_KEYCMP 1

#ifdef USE_HASHED_KEYCMP
#include "hashbase.h"
#endif

/* Max number of terms to do matching for */
#define MAXTERMS 20


class SummaryDesc;
class SummaryConfig;
class QueryTerm;

typedef std::list<MatchCandidate*> match_sequence;

class Matcher : public ITokenProcessor
{
public:
    Matcher(juniper::Result* result);
    virtual ~Matcher();

    Matcher& SetProximityFactor(float proximity_factor);

    /** Call reset_document upon a new document */
    void reset_document();
    void log_document(long id);

    /** Enable logging (generation of long string)
     *  @param log_mask The log feature bits to turn on
     */
    void set_log(unsigned long log_mask);

    /** Token handlers to be called by tokenization step */
    void handle_token(Token& token) override;
    void handle_end(Token& token) override;

    /** Utilities for dump to standard output */
    void dump_matches(int printcount = 10, bool best = false);
    void dump_occurrences(int printcount);
    void dump_statistics();

    /** Utilities for logging to log output buffer */
    void log_matches(int printcount = 10);

    /** Observers:
     * @param  number - the number of the keyword in the order added.
     * @return occurrences of this keyword within document
     */
    int TotalMatchCnt(int number);
    int ExactMatchCnt(int number);

    inline int QueryTerms() { return _mo->TermCount(); }
    const char* QueryTermText(int term_no);

    inline const key_occ_vector& OccurrenceList() { return _occ; }

    // This should ideally be const but no support for const iterators in our multiset:
    inline match_candidate_set& OrderedMatchSet() { return _matches; }

    inline const match_sequence* GetWorkSet() const { return _wrk_set; }

    /* @return Number of hits of any keywords within document */
    inline int TotalHits() { return _occ.size(); }

    /* @return true if this matcher has constraints (NEAR/WITHIN/PHRASE..)
     * applied to the selected match candidate set
     */
    inline bool HasConstraints() { return _mo->HasConstraints(); }
    /* @return true if this matcher uses the validity bits on keyword occurrences */
    inline bool UsesValid() { return _mo->UsesValid(); }

    long GlobalRank();

    // Current size of the document in progress..
    inline size_t DocumentSize() { return _endpos; }

    SummaryDesc* CreateSummaryDesc(size_t length, size_t min_length,
                                   int max_matches,
                                   int surround_len);

    /** Get the log string for this matcher or the empty string if no log enabled */
    std::string GetLog();

    /** Returns the query used by the underlying match object */
    QueryExpr * getQuery() { return _mo->Query(); }

protected:
    /* Internal utilities
     * Those that may fail will return false upon failure.
     */
    bool add_occurrence(off_t pos, off_t tpos, size_t len);
    void reset_matches();
    void reset_occurrences();

    void update_match(MatchCandidate* m);
    void update_wrk_set(match_sequence& ws, MatchElement* k, QueryExpr* mexp);

    // factory methods for creating/referencing/dereferencing MatchCandidates:
    MatchCandidate* NewCandidate(QueryExpr*) __attribute__((noinline));
    MatchCandidate* RefCandidate(MatchCandidate* m);
    void DerefCandidate(MatchCandidate* m);
private:
    Result* _result;
    QueryHandle* _qhandle;
    MatchObject* _mo;
    match_iterator _match_iter;

    //  char* _s;
    // the distance (in characters) between two tokens for them to be considered
    // within same match ("window size" during matching..
    size_t _winsize;
    // Window size used until max_matches has been found.
    size_t _winsizeFallback;
    // The max number of match candidates to manage in the work set for a non-leaf query node.
    size_t _max_match_candidates;

    // A constant to add to the proximity rank value in cases where there are no
    // constraints:
    size_t _proximity_noconstraint_offset;
    double _proximity_factor;

    // if set to >0 attempt to get as many complete matches before
    // winsize is put into effect
    int _need_complete_cnt;

    // Internal state
    size_t _endpos; // The last valid position from the token pipeline

    size_t _nontermcnt;  // The number of nonterminals in the query
    // The sequence of occurrences of the search terms in the document
    key_occ_vector _occ;

    // the current working set of match candidates. This set is now an
    // array of subsets that are the working sets of each query non-terminal
    // Size of this array determined by number of non-terminals in the query
    // using QueryNode->node_idx as lookup.
    match_sequence* _wrk_set;

    // The set of completed match candidates in descending order
    match_candidate_set _matches;

    off_t _ctxt_start;
    unsigned long _log_mask;  // _log_text: a built-up text object with log selectively
    std::string _log_text;    // enabled by _log_mask bits

    Matcher(Matcher &);
    Matcher &operator=(Matcher &);

    void flush_candidates();
    bool markup(const char* t, int len, off_t pos);

    void pushcontext(int ctxt);
    void popcontext(int ctxt);
};

/** Actually build / release the textual summary from a description.
 *  These functions is not dependent of any Matcher info.
 */
std::string BuildSummary(const char* buffer, size_t buflen, SummaryDesc* summary,
			 const SummaryConfig* config, size_t& char_size);
void DeleteSummaryDesc(SummaryDesc*);

