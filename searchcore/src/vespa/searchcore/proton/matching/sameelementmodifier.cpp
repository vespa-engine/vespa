// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sameelementmodifier.h"
#include <vespa/vespalib/util/classname.h>
#include <vespa/log/log.h>
LOG_SETUP(".matching.sameelementmodifier");

namespace proton::matching {

void
SameElementModifier::visit(ProtonNodeTypes::SameElement &n) {
    if (n.getView().empty()) return;

    vespalib::string prefix = n.getView() + ".";
    for (search::query::Node * child : n.getChildren()) {
        search::query::TermNode * term  = dynamic_cast<search::query::TermNode *>(child);
        if (term != nullptr) {
            const vespalib::string & index = term->getView();
            if (index.find(prefix) != 0) { // This can be removed when qrs does not prefix the sameelemnt children
                term->setView(prefix + index);
            }
        } else {
            LOG(error, "Required a search::query::TermNode. Got %s", vespalib::getClassName(*child).c_str());
        }
    }
}

}

