// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "resolveviewvisitor.h"
#include <vespa/document/datatype/positiondatatype.h>
#include <vespa/log/log.h>

LOG_SETUP(".proton.matching.resolveviewvisitor");

namespace proton::matching {

void
ResolveViewVisitor::visit(ProtonLocationTerm &n) {
    // if injected by query.cpp, this should work:
    n.resolve(_resolver, _indexEnv);
    if (n.numFields() == 0) {
        // if received from QRS, this is needed:
        auto oldView = n.getView();
        auto newView = document::PositionDataType::getZCurveFieldName(oldView);
        n.setView(newView);
        n.resolve(_resolver, _indexEnv);
        LOG(info, "ProtonLocationTerm found %zu field after view change %s -> %s",
            n.numFields(), oldView.c_str(), newView.c_str());
    }
}

} // namespace
