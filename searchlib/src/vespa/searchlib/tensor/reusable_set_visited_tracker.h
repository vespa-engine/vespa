// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/reusable_set_handle.h>

namespace search::tensor {

class HnswIndex;

/*
 * Tracker for visited nodes based on vespalib::ReusableSet.
 */
class ReusableSetVisitedTracker
{
    vespalib::ReusableSetHandle _visited;
public:
    ReusableSetVisitedTracker(const HnswIndex& index, uint32_t doc_id_limit, uint32_t);
    ~ReusableSetVisitedTracker();
    void mark(uint32_t doc_id) { _visited.mark(doc_id); }
    bool try_mark(uint32_t doc_id) {
        if (_visited.is_marked(doc_id)) {
            return false;
        } else {
            _visited.mark(doc_id);
            return true;
        }
    }
};

}
