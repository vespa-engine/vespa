// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_saver.h"
#include "document_features_store.h"

namespace search { class BufferWriter; }

namespace search::predicate {

/*
 * Class used to save a DocumentFeaturesStore instance, streaming the
 * serialized data via a BufferWriter.
 */
class DocumentFeaturesStoreSaver : public ISaver {
    using RefsVector = DocumentFeaturesStore::RefsVector;
    using FeaturesStore = DocumentFeaturesStore::FeaturesStore;
    using RangesStore = DocumentFeaturesStore::RangesStore;
    using WordStore = DocumentFeaturesStore::WordStore;

    const RefsVector      _refs;
    const FeaturesStore&  _features;
    const RangesStore&    _ranges;
    const WordStore&      _word_store;
    const uint32_t        _arity;

public:
    DocumentFeaturesStoreSaver(const DocumentFeaturesStore& store);
    ~DocumentFeaturesStoreSaver() override;
    void save(BufferWriter& writer) const override;
};

}
