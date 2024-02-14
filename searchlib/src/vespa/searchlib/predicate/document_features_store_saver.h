// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "document_features_store.h"

namespace search { class BufferWriter; }

namespace search::predicate {

/*
 * Class used to save a DocumentFeaturesStore instance, streaming the
 * serialized data via a BufferWriter.
 */
class DocumentFeaturesStoreSaver {
    using RefsVector = DocumentFeaturesStore::RefsVector;
    using FeaturesStore = DocumentFeaturesStore::FeaturesStore;
    using RangesStore = DocumentFeaturesStore::RangesStore;
    using WordStore = DocumentFeaturesStore::WordStore;

    const RefsVector&     _refs;  // TODO: Use copy when saving in flush thread
    const FeaturesStore&  _features;
    const RangesStore&    _ranges;
    const WordStore&      _word_store;
    const uint32_t        _arity;

public:
    DocumentFeaturesStoreSaver(const DocumentFeaturesStore& store);
    ~DocumentFeaturesStoreSaver();
    void save(BufferWriter& writer) const;
};

}
