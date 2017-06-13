// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::slotfile::SlotMatcher
 * \ingroup memfile
 *
 * \brief Implement this to create a filter for MemSlot instances.
 *
 * Many operations want to do something to a subset of the slots in a file.
 * Such operations can retrieve the slots that matches using an implementation
 * of this filter.
 *
 * Creating a slot matcher, you should give information of what type of data
 * you want to preload from disk. Typically you want to preload entries you
 * need such as to prevent many disk accesses, but if there is some data you
 * only need for a few entries, you can use the functions supplied in the
 * matcher to get these instances even though they are not cached for all
 * entries.
 */

#pragma once

#include <vespa/memfilepersistence/memfile/memslot.h>

namespace storage {
namespace memfile {

class MemFile;

class SlotMatcher : private Types {
public:
    enum PreloadFlag {
        PRELOAD_META_DATA_ONLY = 0x0,
        PRELOAD_BODY           = 0x1,
        PRELOAD_HEADER         = 0x3,
        PRELOAD_DOC_ID         = 0x7
    };

protected:
    SlotMatcher(PreloadFlag preld) : _preload(preld) {}

    PreloadFlag _preload;

public:
    class Slot {
    private:
        const MemSlot& _slot;
        const MemFile& _file;

    public:
        Slot(const MemSlot& slot, const MemFile& file)
            : _slot(slot),
              _file(file) {};

        /**
           Returns the timestamp of the slot.
        */
        Timestamp getTimestamp() const;

        /**
         * Returns whether a slot is a remove, either regular
         * or unrevertable.
         */
        bool isRemove() const;

        /**
           Returns the global id of the slot.
        */
        const GlobalId& getGlobalId() const;

        /**
         * Get the document, optionally just the header. If not preloaded, will load
         * this document from disk.
         */
        Document::UP getDocument(bool headerOnly) const;

        document::DocumentId getDocumentId() const;
    };

    virtual ~SlotMatcher() {}

    virtual bool match(const Slot&) = 0;

    /** Do what is needed to preload wanted content. */
    void preload(MemFile&) const {};
};

} // storage
} // memfile

