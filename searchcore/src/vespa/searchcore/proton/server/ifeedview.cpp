// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "ifeedview.h"
#include <vespa/vespalib/util/destructor_callbacks.h>
#include <vespa/vespalib/util/gate.h>

namespace proton {

void
IFeedView::forceCommitAndWait(CommitParam param) {
    vespalib::Gate gate;
    forceCommit(param, std::make_shared<vespalib::GateCallback>(gate));
    gate.await();
}

}
