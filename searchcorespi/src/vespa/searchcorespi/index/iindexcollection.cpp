// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "iindexcollection.h"
#include "idiskindex.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/searchlib/queryeval/isourceselector.h>

namespace searchcorespi {

using index::IDiskIndex;

vespalib::string IIndexCollection::toString() const
{
    vespalib::asciistream s;
    s << "selector : " << &getSourceSelector() << "(baseId=" << getSourceSelector().getBaseId()
                                               << ", docidlimit=" << getSourceSelector().getDocIdLimit()
                                               << ", defaultsource=" << uint32_t(getSourceSelector().getDefaultSource())
                                               << ")\n";
#if 0
    search::queryeval::ISourceSelector::Iterator::UP it = getSourceSelector().createIterator();
    s << "{";
    for (size_t i(0), m(getSourceSelector().getDocIdLimit()); i < m; i++) {
        s << uint32_t(it->getSource(i)) << ' ';
    }
    s << "}\n";
#endif
    s << getSourceCount() << " {";
    if (getSourceCount() > 0) {
        for (size_t i(0), m(getSourceCount()); i < m; i++) {
            if (i != 0) {
                s << ", ";
            }
            const IndexSearchable & is(getSearchable(i));
            s << getSourceId(i) << " : " << &is << "(";
            if (dynamic_cast<const IDiskIndex *>(&is) != NULL) {
                s << dynamic_cast<const IDiskIndex &>(is).getIndexDir().c_str();
            } else {
                s << typeid(is).name();
            }
            s << ")";
        }
    }
    s << "}";
    return s.str();
}

}
