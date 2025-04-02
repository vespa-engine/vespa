// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace search::attribute {

/**
 * Interface for writing a serialized form of an attribute vector used for sorting.
 * The serialized form can be used by memcmp() and sort order will be preserved.
 */
class ISortBlobWriter {
public:
    virtual ~ISortBlobWriter() = default;

    /**
     * Serialize and write the values for the given document into the given buffer.
     *
     * @param docid     The document id to serialize.
     * @param buf       The buffer to serialize into.
     * @param available Number of bytes available in the buffer.
     * @return The number of bytes written, -1 if not enough space.
     */
    virtual long write(uint32_t docid, void* buf, long available) const = 0;
};

}
