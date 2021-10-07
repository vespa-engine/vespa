// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "strchrfieldsearcher.h"
#include <vespa/document/fieldvalue/stringfieldvalue.h>

using search::streaming::QueryTerm;
using search::streaming::QueryTermList;

namespace vsm {

void StrChrFieldSearcher::prepare(QueryTermList & qtl, const SharedSearcherBuf & buf)
{
  FieldSearcher::prepare(qtl, buf);
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
    for(QueryTermList::iterator it=_qtl.begin(), mt=_qtl.end(); it != mt; it++) {
      QueryTerm & qt = **it;
      if (fieldRef.size() >= qt.termLen()) {
        _words += matchTerm(fieldRef, qt);
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
  for(QueryTermList::const_iterator it=_qtl.begin()+1, mt=_qtl.end(); it != mt; it++) {
    const QueryTerm & qt = **it;
    mintsz = std::min(mintsz, qt.termLen());
  }
  return mintsz;
}

}
