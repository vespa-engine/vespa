// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "uniqueslotgenerator.h"
#include <vespa/memfilepersistence/memfile/memfile.h>
#include <vespa/memfilepersistence/memfile/doccache.h>

namespace storage {

namespace memfile {

bool
UniqueSlotGenerator::ContentLocation::operator==(
        const ContentLocation& other) const
{
    if (_loc.valid() && other._loc.valid()) return _loc == other._loc;
    return _content == other._content;
}

bool
UniqueSlotGenerator::ContentLocation::operator<(
        const ContentLocation& other) const
{
    if (_loc.valid() && other._loc.valid()) return _loc < other._loc;
    if (other._loc.valid()) return false;
    if (_loc.valid()) return true;
    return _content < other._content;
}

void
UniqueSlotGenerator::ContentLocation::print(std::ostream& out, bool,
                                            const std::string&) const
{
    out << "ContentLocation(" << _loc << ", "
        << std::hex << _content << std::dec << ")";
}

UniqueSlotGenerator::UniqueSlotGenerator(const MemFile& memFile)
    : _slots(2),
      _slotsInOrder(2)
{
    for (uint32_t i = 0; i < memFile.getSlotCount(); i++) {
        const MemSlot& slot = memFile[i];
        addSlot(HEADER, slot);
        if (slot.hasBodyContent()) addSlot(BODY, slot);
    }
}

void
UniqueSlotGenerator::addSlot(DocumentPart part, const MemSlot& slot)
{
    ContentLocation contentLoc(slot.getLocation(part));
    if (slot.getDocCache() != NULL) {
        contentLoc._content = slot.getDocCache()->getPart(part).get();
    }
    SlotList& loc = _slots[part][contentLoc];
    loc.push_back(&slot);
    if (loc.size() == 1) {
        _slotsInOrder[part].push_back(&loc);
    }
}

void
UniqueSlotGenerator::print(std::ostream& out, bool verbose,
                           const std::string& indent) const
{
    (void) verbose;
    for (uint32_t i=0; i<2; ++i) {
        DocumentPart part(static_cast<DocumentPart>(i));
        out << getDocumentPartName(part) << ":";
        const OrderedSlotList& list = _slotsInOrder[part];
        for (uint32_t j = 0; j < list.size(); ++j) {
            const SlotList& slotList = *list[j];
            out << "\n" << indent << slotList[0]->getLocation(part) << ": ";
            for (uint32_t k = 0; k < slotList.size(); ++k) {
                if (k > 0) out << ", ";
                out << slotList[k]->getTimestamp();
            }
        }
        if (i == 0) out << "\n";
    }
}

} // memfile
} // storage
