// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "slotmatcher.h"
#include <vespa/memfilepersistence/memfile/memfile.h>

namespace storage {
namespace memfile {

Types::Timestamp
SlotMatcher::Slot::getTimestamp() const
{
    return _slot.getTimestamp();
}

bool
SlotMatcher::Slot::isRemove() const
{
    return _slot.deleted();
}

const document::GlobalId&
SlotMatcher::Slot::getGlobalId() const
{
    return _slot.getGlobalId();
}

document::Document::UP
SlotMatcher::Slot::getDocument(bool headerOnly) const
{
    return _file.getDocument(_slot, headerOnly ? HEADER_ONLY : ALL);
}

document::DocumentId
SlotMatcher::Slot::getDocumentId() const
{
    return _file.getDocumentId(_slot);
}

} // memfile
} // storage
