// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "strchrfieldsearcher.h"
#include <vespa/document/fieldvalue/stringfieldvalue.h>

using search::streaming::QueryTerm;
using search::streaming::QueryTermList;

namespace vsm {

void StrChrFieldSearcher::prepare(search::streaming::QueryTermList& qtl,
                                  const SharedSearcherBuf& buf,
                                  const vsm::FieldPathMapT& field_paths,
                                  search::fef::IQueryEnvironment& query_env)
{
    FieldSearcher::prepare(qtl, buf, field_paths, query_env);
}

void StrChrFieldSearcher::onValue(const document::FieldValue & fv)
{
    const auto & sfv = static_cast<const document::LiteralFieldValueB &>(fv);
    std::string_view val = sfv.getValueRef();
    FieldRef fr(val.data(), std::min(maxFieldLength(), val.size()));
    matchDoc(fr);
}

bool StrChrFieldSearcher::matchDoc(const FieldRef & fieldRef)
{
    size_t element_length = 0;
    bool need_count_words = false;
    if (_qtl.size() > 1) {
        size_t mintsz = shortestTerm();
        if (fieldRef.size() >= mintsz) {
            element_length = matchTerms(fieldRef, mintsz);
        } else {
            need_count_words = true;
        }
    } else {
        for (auto qt : _qtl) {
            if (fieldRef.size() >= qt->termLen() || qt->isRegex() || qt->isFuzzy()) {
                element_length = std::max(element_length, matchTerm(fieldRef, *qt));
            } else {
                need_count_words = true;
            }
        }
    }
    if (need_count_words) {
        element_length = std::max(element_length, countWords(fieldRef));
    }
    set_element_length(element_length);
    return true;
}

size_t StrChrFieldSearcher::shortestTerm() const
{
    size_t mintsz(_qtl.front()->termLen());
    for (auto it=_qtl.begin()+1, mt=_qtl.end(); it != mt; it++) {
        const QueryTerm & qt = **it;
        if (qt.isRegex() || qt.isFuzzy()) {
            return 0; // Must avoid "too short query term" optimization when using regex or fuzzy
        }
        mintsz = std::min(mintsz, qt.termLen());
    }
    return mintsz;
}

}
