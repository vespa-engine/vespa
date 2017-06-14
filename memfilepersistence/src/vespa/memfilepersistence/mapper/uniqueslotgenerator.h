// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::memfile::UniqueSlotGenerator
 * \ingroup memfile
 *
 * Generates a mapping from unique content locations on disk
 * (or unique documents if not persisted) to a list of slots.
 */
#pragma once

#include <vespa/memfilepersistence/common/types.h>

namespace storage {
namespace memfile {

class MemSlot;
class MemFile;

class UniqueSlotGenerator : private Types, public vespalib::Printable
{
public:
    typedef std::vector<const MemSlot*> SlotList;

private:
    struct ContentLocation : public vespalib::Printable {
        DataLocation _loc;
        const document::StructFieldValue* _content;

        ContentLocation(const DataLocation& loc) : _loc(loc), _content(0) {}

        ContentLocation(const DataLocation& loc,
                        const document::StructFieldValue* content)
            : _loc(loc), _content(content) {}

        bool operator<(const ContentLocation& other) const;
        bool operator==(const ContentLocation& other) const;

        void print(std::ostream& out, bool verbose,
                   const std::string& indent) const override;
    };

    void addSlot(DocumentPart, const MemSlot&);

    typedef std::map<ContentLocation, SlotList> LocationToSlotMap;
    typedef std::vector<SlotList*> OrderedSlotList;

    std::vector<LocationToSlotMap> _slots;
    std::vector<OrderedSlotList> _slotsInOrder;

public:
    UniqueSlotGenerator(const MemFile& memFile);

    uint32_t getNumUnique(DocumentPart part) const {
        return _slotsInOrder[part].size();
    }

    const SlotList& getSlots(DocumentPart part, uint32_t uniqueIndex) const {
        return *_slotsInOrder[part][uniqueIndex];
    }

    void print(std::ostream&, bool verbose, const std::string& indent) const override;

};

} // memfile
} // storage

