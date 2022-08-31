// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <algorithm>
#include <string>
#include "query.h"
#include "juniperdebug.h"
#include "sumdesc.h"
#include "Matcher.h"
#include "result.h"
#include "juniperparams.h"
#include "config.h"
#include <sstream>
#include <vespa/log/log.h>
LOG_SETUP(".juniper.matcher");

unsigned debug_level = 0;

#define KEY_OCC_RESERVED 10

Matcher::Matcher(Result* result) :
    _result(result),
    _qhandle(result->_qhandle),
    _mo(_qhandle->MatchObj(result->_langid)),
    _match_iter(_mo, result),
    _winsize(600),
    _winsizeFallback(_winsize*10),
    _max_match_candidates(1000),
    _proximity_noconstraint_offset(PROXIMITYBOOST_NOCONSTRAINT_OFFSET),
    _proximity_factor(1.0),
    _need_complete_cnt(3),
    _endpos(0),
    _nontermcnt(_mo->NontermCount()),
    _occ(),
    _wrk_set(NULL),
    _matches(),
    _ctxt_start(0),
    _log_mask(0),
    _log_text("")
{
    _occ.reserve(KEY_OCC_RESERVED);
    const DocsumParams& dsp = _result->_config->_docsumparams;
    _winsize = _result->WinSize();
    _winsizeFallback = static_cast<size_t>(_result->WinSizeFallbackMultiplier() * _winsize);
    _max_match_candidates = _result->MaxMatchCandidates();
    _need_complete_cnt = dsp.MaxMatches();
    _wrk_set = new match_sequence[_nontermcnt];
    LOG(debug, "Matcher(): winsize(%zu), winsize_fallback(%zu), max_match_candidates(%zu), need_complete_cnt(%d)",
        _winsize, _winsizeFallback, _max_match_candidates, _need_complete_cnt);

}

Matcher::~Matcher()
{
    reset_document();
    delete[] _wrk_set;
}


// Efficient object creation/deletion

MatchCandidate* Matcher::NewCandidate(QueryExpr* query)
{
    typedef MatchElement * MatchElementP;
    return new MatchCandidate(query, new MatchElementP[query->_arity], _ctxt_start);
}


MatchCandidate* Matcher::RefCandidate(MatchCandidate* m)
{
    if (!m) return NULL;
    m->ref();
    if (LOG_WOULD_LOG(spam)) {
        std::string s; m->dump(s);
        LOG(spam, "RefCandidate: %s", s.c_str());
    }
    return m;
}


void Matcher::DerefCandidate(MatchCandidate* m)
{
    if (!m) return;
    if (LOG_WOULD_LOG(spam)) {
        std::string s; m->dump(s);
        LOG(spam, "DerefCandidate: %s", s.c_str());
    }
    if (m->deref()) return;
    // Dereference all the complex (MatchCandidate) children of m:
    for (int i = 0; i < m->elem_store_sz(); i++) {
        if (m->element[i])
            DerefCandidate(m->element[i]->Complex());
    }
    delete m;
}


Matcher& Matcher::SetProximityFactor(float proximity_factor)
{
    if (proximity_factor != 1) {
        LOG(debug, "Proximity factor %.1f", proximity_factor);
    }
    _proximity_factor = proximity_factor;
    return *this;
}


void Matcher::reset_document()
{
    // Delete all our document specific data structures to reset to initial state:
    LOG(debug, "Matcher: resetting document");
    flush_candidates();
    reset_matches();
    reset_occurrences();
    _endpos = 0;
}

void Matcher::reset_matches()
{
    LOG(debug, "reset_matches");
    for (match_candidate_set::iterator it = _matches.begin(); it != _matches.end(); ++it)
        DerefCandidate(*it);
    _matches.clear();
    _ctxt_start = 0;
}

void Matcher::reset_occurrences()
{
    _occ.clear();
}


void Matcher::update_match(MatchCandidate* m)
{
    QueryNode* nexp = m->match()->_parent;
    if (!nexp) { // root node of query
        _matches.insert(m);
        // Tag all terms
        m->set_valid();
    } else {
        // Add the parent candidate
        MatchCandidate* nm = NewCandidate(nexp);
        match_sequence& cs = _wrk_set[nexp->_node_idx];
        cs.push_back(nm);

        // Update the parent candidate work set
        update_wrk_set(_wrk_set[nexp->_node_idx], m, m->match());

        // This candidate was removed from it's wrk set but
        // the ref is not forwarded to the matches list since it is an
        // intermediate node..
        DerefCandidate(m);
    }
}



bool Matcher::add_occurrence(off_t pos, off_t tpos, size_t len)
{
    QueryTerm* mexp = _match_iter.current();

    LOG(spam, "Match: %s(%" PRId64 ")", mexp->term(), static_cast<int64_t>(tpos));

    // Add new occurrence to sequence of all occurrences
    auto smart_k = std::make_unique<key_occ>(mexp->term(), pos, tpos, len);
    if (!smart_k) return false;

    auto k = smart_k.get();
    _occ.emplace_back(std::move(smart_k));

    if (!(_need_complete_cnt > 0)) {
        size_t nodeno;
        // From the head of the sequences, remove any candidates that are
        // "too old", eg. that is not complete within the winsize window
        // and also trig further processing of complete matches:
        for (nodeno = 0; nodeno < _nontermcnt; nodeno++) {
            match_sequence& ws = _wrk_set[nodeno];
            for (match_sequence::iterator it = ws.begin(); it != ws.end();) {
                MatchCandidate* m = (*it);
                if ((k->startpos() - m->startpos()) < static_cast<int>(_winsize)) break;
                it = ws.erase(it); // This moves the iterator forward
                if (m->partial_ok())
                    update_match(m);
                else
                    DerefCandidate(m);
            }
        }
    }

    // Then add a new candidate starting at the currently found keyword
    // for each subexpression that matches this keyword
    for (; mexp != NULL; mexp = _match_iter.next())
    {
        QueryNode* pexp = mexp->_parent;
        assert(pexp);
        MatchCandidate* nm = NewCandidate(pexp);
        if (!nm || nm->elems() < 0) {
            LOG(error, "Matcher could not allocate memory for candidate - bailing out");
            if (nm) DerefCandidate(nm);
            return false;
        }
        match_sequence& cs = _wrk_set[pexp->_node_idx];
        if (cs.size() >= _max_match_candidates) {
            DerefCandidate(nm);
            LOG(debug, "The max number of match candidates (%zu) in the work set for query node idx '%u' has been reached. "
                "No more candidates are added", _max_match_candidates, pexp->_node_idx);
        } else {
            cs.push_back(nm);
        }
        update_wrk_set(cs, k, mexp);
    }
    return true;
}



void Matcher::update_wrk_set(match_sequence& ws, MatchElement* k, QueryExpr* mexp)
{
    if (LOG_WOULD_LOG(spam)) {
        std::string s; k->dump(s);
        LOG(spam, "update_wrk_set(): match_sequence.size(%zu), element(%s)", ws.size(), s.c_str());
    }

    // update this working set (start with the freshest)
    for (match_sequence::reverse_iterator rit = ws.rbegin(); rit != ws.rend();) {
        MatchCandidate* m = (*rit);

        MatchCandidate::accept_state as = m->accept(k, mexp);

        // If a candidate already has this keyword, then all earlier
        // candidates also has the keyword
        if (as == MatchCandidate::M_EXISTS) break;


        // Just accepted this candidate into another higher level
        if (as != MatchCandidate::M_OVERLAP) {
            MatchCandidate* mu = k->Complex();
            RefCandidate(mu);
        }

        // we should allow a slighly larger winsize here because we have not found all matches yet.
        if ((as == MatchCandidate::M_EXPIRED) || ((k->startpos() - m->startpos()) >= static_cast<int>(_winsizeFallback))) {
            // remove from current pos and delete - can never be satisfied
            match_sequence::reverse_iterator new_rit(ws.erase((++rit).base()));
            rit = new_rit;
            DerefCandidate(m);
        } else {
            // If this one got complete, move it to the ranked set or trigger updates
            // of parent candidates if subquery match
            if (m->complete()) {
                // STL hackers' heaven - removing this element unconditionally from _wrk_set['k']
                match_sequence::reverse_iterator new_rit(ws.erase((++rit).base()));
                rit = new_rit;

                if (m->matches_limit()) {
                    if (_need_complete_cnt > 0) {
                        _need_complete_cnt--;
                    }
                    update_match(m);
                } else {
                    DerefCandidate(m);
                }
            } else {
                ++rit;
            }
        }
    }
    if (LOG_WOULD_LOG(spam)) {
        std::string s; k->dump(s);
        LOG(spam, "END update_wrk_set, '%s'", s.c_str());
    }
}


// Flush all remaining candidates upon context change or document end:
void Matcher::flush_candidates()
{
    int cands = 0;
    for (size_t i = 0; i < _nontermcnt; i++) {
        match_sequence& ws = _wrk_set[i];
        for (match_sequence::iterator it = ws.begin(); it != ws.end(); ++it) {
            cands++;
            MatchCandidate* m = (*it);
            if (m->partial_ok())
                update_match(m);
            else
                DerefCandidate(m);
        }
        ws.clear();
    }
    LOG(debug, "Flushing done (%d candidates)", cands);
}


void Matcher::set_log(unsigned long log_mask)
{
    _log_mask = log_mask;
}


void Matcher::handle_token(Token& token)
{
    if (LOG_WOULD_LOG(debug)) {
        char utf8token[1024];
        Fast_UnicodeUtil::utf8ncopy(utf8token, token.token, 1024,
                                    (token.token != NULL ? token.curlen : 0));
        LOG(debug, "handle_token(%s)", utf8token);
    }

    unsigned options = 0;
    if (_mo->Match(_match_iter, token, options)) {
        // Found a match. Record it with original pos and length
        add_occurrence(token.bytepos, token.wordpos, token.bytelen);
    }
    // Keep track of end of the text
    _endpos = token.bytepos + token.bytelen;
}


void Matcher::handle_end(Token& token)
{
    if (LOG_WOULD_LOG(debug)) {
        char utf8token[1024];
        Fast_UnicodeUtil::utf8ncopy(utf8token, token.token, 1024,
                                    (token.token != NULL ? token.curlen : 0));
        LOG(debug, "handle_end(%s)", utf8token);
    }
    if (LOG_WOULD_LOG(spam)) {
        dump_occurrences(100);
        LOG(spam, "Topmost 10 matches found:");
        dump_matches(10, false);
    }
    JL(JD_MDUMP, log_matches(20));
    // Just keep track of end of the text
    _endpos = token.bytepos;
    // flush here for now since we do not traverse all the nonterminal lists for each kw.
    flush_candidates();
}


void Matcher::dump_matches(int printcount, bool best)
{
    assert(!best); // This functionality removed
    match_candidate_set& m = _matches;

    if (!best) {
        // flush the remaining match candidates to the list of matches, if any:
        flush_candidates();
    }
    int i = 0;
    std::ostringstream oss;
    oss << "dump_matches(" << m.size() << "):\n";
    i = 0;
    for (match_candidate_set::iterator it = m.begin(); it != m.end(); ++it) {
        if (i >= printcount) break;
//    if ((*it)->distance() == 0) break;
        std::string s;
        (*it)->dump(s);
        oss << s << "\n";
        i++;
    }
    LOG(spam, "%s", oss.str().c_str());
}


void Matcher::log_matches(int printcount)
{
    int nterms = QueryTerms();
    match_candidate_set& m = _matches;

    // flush the remaining match candidates to the list of matches, if any:
    flush_candidates();
    char buf[200];

    int i = 0;
    _log_text.append("<table>");
    if (m.size() > 0) {
        _log_text.append("<tr class=shade>");
        sprintf(buf, "<td colspan=%d align=center><b>Topmost %zu matches out of %zu",
                nterms+2, std::min(static_cast<size_t>(printcount), m.size()),m.size());
        _log_text.append(buf);
        _log_text.append("</b></td></tr>");
    }
    _log_text.append("<tr class=shadehead>");
    for (i = 0; i < nterms; i++) {
        _log_text.append("<td>");
        _log_text.append(_mo->Term(i)->term());
        _log_text.append("</td>");
    }
    if (m.size() > 0) {
        _log_text.append("<td align=right>distance</td><td align=right>rank</td></tr>\n");
        i = 0;
        for (match_candidate_set::iterator it = m.begin(); it != m.end(); ++it)
        {
            if (i >= printcount) break;
            _log_text.append("<tr class=shade>");
            (*it)->log(_log_text);
            _log_text.append("</tr>");
            i++;
        }
    }
    _log_text.append("<tr class=shadehead>");
    sprintf(buf, "<td colspan=%d align=center><b>Total(exact) keyword hits</b></td>",
            nterms);
    _log_text.append(buf);
    _log_text.append("</tr><tr class=shade>");
    for (i = 0; i < nterms; i++) {
        sprintf(buf, "<td>%d(%d)</td>", TotalMatchCnt(i), ExactMatchCnt(i));
        _log_text.append(buf);
    }
    _log_text.append("</tr></table>");
}



void Matcher::dump_occurrences(int printcount)
{
    std::ostringstream oss;
    oss << "dump_occurrences:\n";
    int i = 0;
    for (key_occ_vector::iterator kit = _occ.begin(); kit != _occ.end(); ++kit) {
        std::string s;
        (*kit)->dump(s);
        oss << s << "\n";
        i++;
        if (i > printcount) {
            oss << "...cont...\n";
            break;
        }
    }
    LOG(spam, "%s", oss.str().c_str());
}


void Matcher::dump_statistics()
{
    int i;
    int nterms = QueryTerms();

    fprintf(stderr, "%20s %12s %12s\n", "Term", "Matches", "Exact");
    for (i = 0; i < nterms; i++) {
        QueryTerm* q = _mo->Term(i);
        fprintf(stderr, "%20s %12d %12d\n", q->term(), q->total_match_cnt,
                q->exact_match_cnt);
    }
}



// Debugging/testing:

int Matcher::TotalMatchCnt(int number)
{
    if (number < QueryTerms() && number >= 0)
        return _mo->Term(number)->total_match_cnt;
    else
        return 0;
}


int Matcher::ExactMatchCnt(int number)
{
    if (number < QueryTerms() && number >= 0)
        return _mo->Term(number)->exact_match_cnt;
    else
        return 0;
}


const char* Matcher::QueryTermText(int term_no)
{
    return _mo->Term(term_no)->term();
}


std::string Matcher::GetLog()
{
    return _log_text;
}


SummaryDesc* Matcher::CreateSummaryDesc(size_t length, size_t min_length, int max_matches, int surround_len)
{
    // No point in processing this document if no keywords found at all:
    if (TotalHits() <= 0) return NULL;

    LOG(debug, "Matcher: sum.desc (length %lu, min_length %lu, max matches %d, "
        "surround max %d)",
        static_cast<unsigned long>(length),
        static_cast<unsigned long>(min_length),
        max_matches, surround_len);
    return new SummaryDesc(this, length, min_length, max_matches, surround_len);
}


// This should rather be called ProximityRank() now:
long Matcher::GlobalRank()
{
    // Proximity ranking only applies to multi term queries, return a constant
    // in all other cases:
    if (QueryTerms() <= 1) return _proximity_noconstraint_offset;

    match_candidate_set::iterator it = _matches.begin();
#ifdef JUNIPER_1_0_RANK
    if (it == _matches.end()) return 0;

    // Rank is computed as the rank of the best match within the document
    // boosted with the total number of found occurrences of any of the words in the query
    // normalized by the number of words in the query:
    return ((*it)->rank() >> 3) + ((TotalHits()/nterms) << 2);
#else
    // Rank is computed as the rank of the 3 best matches within the document
    // with each subsequent match counting 80% of the previous match.
    //
    long rank_val = 0;
    const int quotient = 5;
    const int prod = 4;
    int r_quotient = 1;
    int r_prod = 1;
    const int best_matches = 3; // candidate(s) for parametrisation!

    for (int i = 0; i < best_matches && it != _matches.end(); i++) {
        rank_val += (((*it)->rank()*r_prod/r_quotient) >> 4);
        r_quotient *= quotient;
        r_prod *= prod;
        ++it;
    }

    // Return negative weight of no hits and any of the explicit limits in effect
    // Eg. NEAR/WITHIN but make exception for PHRASE since that is better
    //handled by the index in the cases where there are more information at that stage:
    if (!rank_val && _mo->HasConstraints())
        return 0;

    // shift down to a more suitable range for fsearch. Multiply by configured boost
    // Add configured offset
    return (long)((double)(rank_val >> 1) * _proximity_factor) + _proximity_noconstraint_offset;
#endif
}


/* These operations can be performed after the matcher is no longer existing..
 *
 */
std::string BuildSummary(const char* buffer, size_t buflen, SummaryDesc* summary,
			 const SummaryConfig* config, size_t& char_size)
{
    return summary->get_summary(buffer, buflen, config, char_size);
}


void DeleteSummaryDesc(SummaryDesc* s)
{
    LOG(debug, "Matcher: deleting SummaryDesc");
    delete s;
}
