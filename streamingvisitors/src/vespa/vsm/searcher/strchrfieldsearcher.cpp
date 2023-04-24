// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    const document::LiteralFieldValueB & sfv = static_cast<const document::LiteralFieldValueB &>(fv);
    vespalib::stringref val = sfv.getValueRef();
    FieldRef fr(val.data(), std::min(maxFieldLength(), val.size()));
    matchDoc(fr);
}

bool StrChrFieldSearcher::matchDoc(const FieldRef & fieldRef)
{
  bool retval(true);
  if (_qtl.size() > 1) {
    size_t mintsz = shortestTerm();
    if (fieldRef.size() >= mintsz) {
      _words += matchTerms(fieldRef, mintsz);
    } else {
      _words += countWords(fieldRef);
    }
  } else {
    for (auto qt : _qtl) {
      if (fieldRef.size() >= qt->termLen()) {
        _words += matchTerm(fieldRef, *qt);
      } else {
        _words += countWords(fieldRef);
      }
    }
  }
  return retval;
}

size_t StrChrFieldSearcher::shortestTerm() const
{
  size_t mintsz(_qtl.front()->termLen());
  for (auto it=_qtl.begin()+1, mt=_qtl.end(); it != mt; it++) {
    const QueryTerm & qt = **it;
    mintsz = std::min(mintsz, qt.termLen());
  }
  return mintsz;
}

}
