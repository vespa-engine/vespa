// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search { class BufferWriter; }

namespace search::tensor {

/**
 * Interface that is used to save a nearest neighbor index to binary form.
 *
 * An instance of this interface must hold a snapshot of the index from the
 * point in time the instance was created, and then save this to binary form in the save() function.
 *
 * The instance is always created by the attribute write thread,
 * and the caller ensures that an attribute read guard is held during the lifetime of the saver.
 * Data that might change later must be copied in the constructor.
 *
 * A flush thread is calling save() at a later point in time.
 */
class NearestNeighborIndexSaver {
public:
    virtual ~NearestNeighborIndexSaver() {}
    virtual void save(BufferWriter& writer) const = 0;
};

}
