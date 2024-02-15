// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "predicate_index_saver.h"
#include "nbo_write.h"

namespace search::predicate {

PredicateIndexSaver::PredicateIndexSaver(std::unique_ptr<ISaver> features_store_saver,
                                         uint32_t arity,
                                         ZeroConstraintDocs zero_constraint_docs,
                                         std::unique_ptr<ISaver> interval_index_saver,
                                         std::unique_ptr<ISaver> bounds_index_saver)
    : ISaver(),
      _features_store_saver(std::move(features_store_saver)),
      _arity(arity),
      _zero_constraint_docs(std::move(zero_constraint_docs)),
      _interval_index_saver(std::move(interval_index_saver)),
      _bounds_index_saver(std::move(bounds_index_saver))
{
}

PredicateIndexSaver::~PredicateIndexSaver() = default;

void
PredicateIndexSaver::save(BufferWriter& writer) const
{
    _features_store_saver->save(writer);
    nbo_write<uint16_t>(writer, _arity);
    nbo_write<uint32_t>(writer, _zero_constraint_docs.size());
    for (auto it = _zero_constraint_docs.begin(); it.valid(); ++it) {
        nbo_write<uint32_t>(writer, it.getKey());
    }
    _interval_index_saver->save(writer);
    _bounds_index_saver->save(writer);
}

}
