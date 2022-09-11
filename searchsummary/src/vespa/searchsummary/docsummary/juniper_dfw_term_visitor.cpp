// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "juniper_dfw_term_visitor.h"
#include "juniper_dfw_explicit_item_data.h"
#include <vespa/juniper/query.h>

namespace search::docsummary {

void
JuniperDFWTermVisitor::visitProperty(const search::fef::Property::Value &key, const search::fef::Property &values)
{
    JuniperDFWExplicitItemData data;
    JuniperDFWQueryItem item(&data);
    int index = 0;
    int numBlocks = atoi(values.getAt(index++).c_str());
    data._index = key;

    _visitor->VisitAND(&item, numBlocks);

    for (int i = 0; i < numBlocks; i++) {
        const search::fef::Property::Value * s = & values.getAt(index++);
        if ((*s)[0] == '"') {
            s = & values.getAt(index++);
            int phraseLen = atoi(s->c_str());
            _visitor->VisitPHRASE(&item, phraseLen);
            s = & values.getAt(index++);
            while ((*s)[0] != '"') {
                _visitor->VisitKeyword(&item, s->c_str(), s->length());
                s = & values.getAt(index++);
            }
        } else {
            _visitor->VisitKeyword(&item, s->c_str(), s->length());
        }
    }
}

}
