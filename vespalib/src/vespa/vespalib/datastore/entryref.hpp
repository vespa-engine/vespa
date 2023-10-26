// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "entryref.h"
#include <vespa/vespalib/util/assert.h>

namespace vespalib::datastore {

template <uint32_t OffsetBits, uint32_t BufferBits>
EntryRefT<OffsetBits, BufferBits>::EntryRefT(size_t offset_, uint32_t bufferId_) noexcept :
    EntryRef((bufferId_ << OffsetBits) + offset_)
{
    ASSERT_ONCE_OR_LOG(offset_ < offsetSize(), "EntryRefT.offset_overflow", 10000);
    ASSERT_ONCE_OR_LOG(bufferId_ < numBuffers(), "EntryRefT.bufferId_overflow", 10000);
}

}
