// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/datastore/array_store_type_mapper.h>

namespace vespalib::datastore {

template <typename EntryT> class SmallArrayBufferType;
template <typename EntryT> class LargeArrayBufferType;

}

namespace search::attribute {

/*
 * This class provides mapping between type ids and array sizes needed for
 * storing a raw value.
 */
class RawBufferTypeMapper : public vespalib::datastore::ArrayStoreTypeMapper
{
public:
    using SmallBufferType = vespalib::datastore::SmallArrayBufferType<char>;
    using LargeBufferType = vespalib::datastore::LargeArrayBufferType<char>;

    RawBufferTypeMapper();
    RawBufferTypeMapper(uint32_t max_small_buffer_type_id, double grow_factor);
    ~RawBufferTypeMapper();
};

}
