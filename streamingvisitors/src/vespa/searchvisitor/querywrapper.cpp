// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "querywrapper.h"

using namespace search::streaming;

namespace streaming {

QueryWrapper::QueryWrapper(Query& query) : _query(query), _termList(), _label_wrappers() {
    _query.getLeaves(_termList);
    // Labeled wrappers are not leaves, but are addressed by rank features just like terms
    LabelWrapperQueryNode::collect(_query.getRoot(), _label_wrappers);
}

QueryWrapper::~QueryWrapper() = default;

} // namespace streaming
