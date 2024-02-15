// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_saver.h"
#include "common.h"

namespace search::predicate {

/*
 * Class used to save a PredicateIndex instance, streaming the
 * serialized data via a BufferWriter.
 */
class PredicateIndexSaver : public ISaver {
    std::unique_ptr<ISaver> _features_store_saver;
    uint32_t                _arity;
    ZeroConstraintDocs      _zero_constraint_docs;
    std::unique_ptr<ISaver> _interval_index_saver;
    std::unique_ptr<ISaver> _bounds_index_saver;
public:
    PredicateIndexSaver(std::unique_ptr<ISaver> features_store_saver,
                        uint32_t _arity,
                        ZeroConstraintDocs zero_constraint_docs,
                        std::unique_ptr<ISaver> interval_index_saver,
                        std::unique_ptr<ISaver> bounds_index_saver);
    ~PredicateIndexSaver() override;
    void save(BufferWriter& writer) const override;
};

}
