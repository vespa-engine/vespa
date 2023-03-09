// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/datastore/atomic_entry_ref.h>
#include <vespa/vespalib/util/rcuvector.h>

namespace search { class AttributeVector; }

namespace vespalib { class Executor; }

namespace search::attribute {

class BlobSequenceReader;
class RawBufferStore;

/**
 * Class for loading a single raw attribute.
 */
class SingleRawAttributeLoader
{
    using AtomicEntryRef = vespalib::datastore::AtomicEntryRef;
    using RefVector = vespalib::RcuVectorBase<AtomicEntryRef>;

    AttributeVector& _attr;
    RefVector&       _ref_vector;
    RawBufferStore&  _raw_store;

    void load_raw_store(BlobSequenceReader& reader, uint32_t docid_limit);
public:
    SingleRawAttributeLoader(AttributeVector& attr, RefVector& ref_vector, RawBufferStore& raw_store);
    ~SingleRawAttributeLoader();
    bool on_load(vespalib::Executor*);
};

}
