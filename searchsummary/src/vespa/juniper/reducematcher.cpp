// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "juniperdebug.h"
#include "reducematcher.h"
#include "querynode.h"

#include <vespa/log/log.h>
LOG_SETUP(".juniper.reducematcher");

namespace juniper {

ReduceMatcher::ReduceMatcher()
    : _matchers()
{ }

void string_matcher::add_term(QueryTerm* t)
{
    string_match_table::iterator it = _table.find(t->term());
    if (it == _table.end())
    {
        std::pair<string_match_table::iterator,bool> p =
            _table.insert(std::make_pair(t->term(), std::vector<QueryTerm*>()));
        it = p.first;
    }
    it->second.push_back(t);
}


ReduceMatcher::~ReduceMatcher()
{
    _matchers.delete_second();
}


string_matcher* ReduceMatcher::find(Rewriter* rw)
{
    string_matcher* sm = _matchers.find(rw);
    if (!sm)
        sm = _matchers.insert(rw, new string_matcher(rw));
    return sm;
}


const std::vector<QueryTerm*>* ReduceMatcher::match(uint32_t langid,
        const char* term, size_t len)
{
    std::vector<QueryTerm*>* vp = NULL;
    // Try each of the matchers
    for (std::map<Rewriter*,string_matcher*>::iterator mit = _matchers.begin();
	 mit != _matchers.end();          ++mit)
    {
        string_matcher* m = mit->second;
        // Expand term to all its forms:
        RewriteHandle* rh = m->rewriter()->Rewrite(langid, term, len);

        size_t elen;
        const char* eterm = m->rewriter()->NextTerm(rh, elen);
        while (eterm)
        {
            std::string t(eterm, elen);
            string_match_table::iterator sit = m->lookup(t);
            if (LOG_WOULD_LOG(spam)) {
                std::string s(m->dump());
                LOG(spam, "(reduction) matching '%s' with %s",
                    t.c_str(), s.c_str());
            }
            if (sit != m->table().end())
            {
                if (!vp) { vp = new std::vector<QueryTerm*>(sit->second);
                } else {
                    vp->insert(vp->end(),sit->second.begin(), sit->second.end()); }
            }
            eterm = m->rewriter()->NextTerm(rh, elen);
        }
    }  // for (each matcher)
    LOG(spam, "reduction yielded %ld query term hits", (vp ? vp->size() : 0l));
    return vp;
} // match()


std::string string_matcher::dump()
{
    std::string s("[");
    for (string_match_table::iterator it = _table.begin();
	 it != _table.end();    ++it)
    {
        s = s + it->first.c_str() + " ";
    }
    s = s + "]";
    return s;
}

} // end namespace juniper
