// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::slotfile::MemFilePtr
 * \ingroup memfile
 *
 * \brief Utility class for managing an entry taken from cache.
 *
 * To be able to seamlessly return copy by value objects from the cache that
 * can be used, and automatically return to the cache on destruction, this
 * wrapper class exist to ensure that then the last user stops using it, it
 * will be released.
 *
 * This object is created by the cache and returned to the disk thread using it.
 * A linked pointer should thus be safe as we assume all users of it will be in
 * the same thread. It assumes the cache itself has a lifetime longer than this
 * object.
 */

#pragma once

namespace storage {
namespace memfile {

class MemFile;

class MemFilePtr {
public:
    /**
     * Utility class to ensure we call done() on cache after all cache
     * pointers are deleted. The cache implements a subclass of this class
     * doing it, to prevent cyclic dependency with cache.
     */
    struct EntryGuard {
        using SP = std::shared_ptr<EntryGuard>;

        MemFile* _file;

        EntryGuard(MemFile& file) : _file(&file) {}
        virtual ~EntryGuard() {}

        virtual void erase() = 0;
        virtual void deleteFile() = 0;
        virtual void move(EntryGuard& target) = 0;
    };

private:
    EntryGuard::SP _entry;

public:
    MemFilePtr() {};
    MemFilePtr(EntryGuard::SP entry) : _entry(std::move(entry)) {}

    // Behave like pointer to MemFile for ease of use.
    MemFile* operator->() { return _entry->_file; }
    MemFile& operator*() { return *_entry->_file; }
    MemFile* get() {
        return (_entry.get() != 0 ? _entry->_file : 0);
    }
    const MemFile* operator->() const { return _entry->_file; }
    const MemFile& operator*() const { return *_entry->_file; }
    const MemFile* get() const {
        return (_entry.get() != 0 ? _entry->_file : 0);
    }

    /** Removes the entry from cache and deletes the underlying file. */
    void deleteFile() { _entry->deleteFile(); }

    /**
     * Erases the entry from the cache. Does not touch the underlying file so
     * therefore requires the memfile's alteredSlots() to return false.
     */
    void eraseFromCache() { _entry->erase(); }

    /**
     * Removes the entry from cache and renames the underlying file.
     * The end result is that this mem file now points to the renamed file.
     * The target MemFilePtr is invalid after this operation.
     *
     * @return Returns false if the target file already existed.
     */
    void move(MemFilePtr& target) {
        _entry->move(*target._entry);
    }
};

} // storage
} // memfile

