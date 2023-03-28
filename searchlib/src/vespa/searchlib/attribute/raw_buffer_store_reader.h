// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/datastore/entryref.h>
#include <vespa/vespalib/stllike/allocator.h>
#include <vector>

namespace search::attribute {

class BlobSequenceReader;
class RawBufferStore;

/**
 * Class for reading raw values into a raw buffer store from a
 * BlobSequenceReader.
 */
class RawBufferStoreReader
{
    RawBufferStore&     _store;
    BlobSequenceReader& _reader;
    std::vector<char, vespalib::allocator_large<char>> _buffer;
public:
    RawBufferStoreReader(RawBufferStore& store, BlobSequenceReader& reader);
    ~RawBufferStoreReader();
    vespalib::datastore::EntryRef read();
};

}
