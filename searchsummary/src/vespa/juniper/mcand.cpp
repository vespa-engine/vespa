// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mcand.h"
#include "Matcher.h"
#include "juniperdebug.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/log/log.h>
LOG_SETUP(".juniper.mcand");

// Invariant: elms has room for query->_arity match element pointers
MatchCandidate::MatchCandidate(QueryExpr* m, MatchElement** elms, off_t ctxt_start) :
    MatchElement(0, 0),
    element(elms),
    _match(m),
    _nelems(0),
    _elems(std::max(m->_arity, 1)),
    _endpos(0),
    _endtoken(0),
    _docid(0),
    _ctxt_start(ctxt_start),
    _elem_weight(0),
    _options(m->_options),
    _overlap(0),
    _refcnt(1),
    _klist()
{
    for (int i = 0; i < _elems; i++)
        element[i] = NULL;

    if (LOG_WOULD_LOG(debug)) {
       std::string s;
       dump(s);
       LOG(debug, "new %s", s.c_str());
    }
}


MatchCandidate::~MatchCandidate()
{
    delete[] element;
}

void MatchCandidate::dump(std::string& s)
{
    int i;
    s.append("MC<");
    for (i = 0; i < _elems; i++)
    {
        if (i > 0) s.append(";");
        _match->AsNode()->_children[i]->Dump(s);
        s.append(":");
        if (element[i])
        {
            s.append(vespalib::make_string("%" PRId64,
                                           static_cast<int64_t>
                                           (element[i]->starttoken())));
            if (element[i]->starttoken() + 1 < element[i]->endtoken())
                s.append(vespalib::make_string("-%" PRId64,
                                               static_cast<int64_t>
                                               (element[i]->endtoken())));
        }
        else
            s.append("<nil>");
    }
    s.append(">");
}


void MatchCandidate::set_valid()
{
    for (int j = 0; j < _elems; j++)
        if (element[j])
            element[j]->set_valid();
    _valid = true;
}


void MatchCandidate::make_keylist()
{
    add_to_keylist(_klist);
}


void MatchCandidate::add_to_keylist(keylist& kl)
{
    if (kl.size() > 0) return; // already made list
    for (int i = 0; i < _elems; i++)
    {
        MatchElement* me = element[i];
        if (me) me->add_to_keylist(kl);
    }
}


MatchCandidate::accept_state MatchCandidate::accept(MatchElement* k, QueryExpr* mexp)
{
    if (element[mexp->_childno]) {
        if (_overlap) return M_OVERLAP;
        return M_EXISTS;
    } else {
        if (order()) {
            // Ensure that overlapping matches are not considered in ordered mode..
            if (k->startpos() < _endpos) {
                _overlap++;
                return M_OVERLAP;
            } else {
                _overlap--; // Found overlap..
            }
        }

        element[mexp->_childno] = k;

        // Note that in 2.1.x match elements are no longer arriving in position order!
        // They may also overlap (because they may be complex candidates themselves)
        if (!_nelems || (k->startpos() < _startpos)) {
            _startpos = k->startpos();
            _starttoken = k->starttoken();
        }
        _nelems++;

        // Update 2.0 term weight/element count/combined element word length
        _elem_weight += weight(k, mexp);

        if (!_nelems || (k->endpos() > _endpos)) {
            _endpos = k->startpos() + k->length();
            _endtoken = k->starttoken() + k->word_length();
        }
        if (LOG_WOULD_LOG(spam)) {
            std::string s("(accept:"); k->dump(s);
            s.append(") "); dump(s);
            LOG(spam, "%s", s.c_str());
        }
        return M_OK;
    }
}


int MatchCandidate::weight(MatchElement* me, QueryExpr* mexp)
{
    QueryTerm* texp = mexp->AsTerm();
    if (texp) return mexp->_weight;
    MatchCandidate* m = reinterpret_cast<MatchCandidate*>(me);
    return m->weight();
}

bool MatchCandidate::complete()
{
    if (_nelems < _elems) return false;
    for (int i = 0; i < _elems; i++)
        if (!element[i]->complete()) return false;
    return true;
}


void MatchCandidate::log(std::string& logobj)
{
    char buf[200];
    for (int i = 0; i < _elems; i++)
    {
        if (element[i])
        {
            sprintf(buf, "<td align=left>%" PRId64 "</td>",
                    static_cast<int64_t>(element[i]->starttoken()));
            logobj.append(buf);
        }
        else
            logobj.append("<td></td>");
    }
    sprintf(buf, "<td align=right>%d</td><td align=right>%d</td>", word_distance(),rank());
    logobj.append(buf);
}

// Check optional WITHIN(limit) constraints:
bool MatchCandidate::matches_limit()
{
    if (!match()->HasLimit()) return true;

    // completeness check:
    if (!complete()) return false;

    int limit = match()->Limit();
    size_t elem_word_len = element[0]->word_length();
    for (int i = 1; i < _elems; i++)
    {
        int  prev_term = i - 1;
        elem_word_len += element[i]->word_length();
        // Order check:
        if (order() && element[prev_term]->starttoken() >= element[i]->starttoken())
            return false;
    }

    // Then check that within total limit:
    if (((int)word_length() - (int)elem_word_len) > limit * (_elems - 1)) return false;
    return true;
}

bool gtematch_cand::gtDistance(const MatchCandidate* m1, const MatchCandidate* m2) const
{
   int m1d(m1->word_distance()), m2d(m2->word_distance());
   return (m1d < m2d)
       ? true
       : (m1d > m2d)
           ? false
           : m1->startpos() < m2->startpos();
}

// A suitable comparator for MatchCandidates
bool gtematch_cand::operator()(const MatchCandidate* m1, const MatchCandidate* m2) const
{
    // replace return m1->rank() > m2->rank();
    // which does return (_elem_weight << 11) - (word_distance() << 8) - (_startpos >> 8);
    return (m1->weight() > m2->weight())
           ? true
           : (m1->weight() < m2->weight())
               ? false
               : gtDistance(m1, m2);
}
