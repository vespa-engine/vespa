// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Keep an LRU order of a given size. This is a utility class for adding a
 * secondary order to some other container.
 */
#pragma once

#include <vespa/document/util/printable.h>
#include <vector>
#include <vespa/vespalib/util/exceptions.h>

namespace storage {
namespace lib {

template<typename Value, typename RemoveFunctor, bool staticSize = true>
class LruOrder : public document::Printable {
public:
    class EntryRef {
        uint32_t _entryIndex;
    public:
        EntryRef() : _entryIndex(0xffffffffu) {}
        EntryRef(uint32_t index) : _entryIndex(index) {}
        friend class LruOrder;
    };

private:
    struct Entry {
        Value _value;
        bool _inUse;
        uint32_t _previous;
        uint32_t _next;

    public:
        Entry() : _value(), _inUse(false),
                 _previous(0xffffffffu), _next(0xffffffffu) {}
    };

    RemoveFunctor& _removeFunctor;
    std::vector<Entry> _entries;

    Entry& first() { return _entries[0]; }
    Entry& last() { return _entries[1]; }
    Entry& previous(Entry& e) { return _entries[e._previous]; }
    Entry& next(Entry& e) { return _entries[e._next]; }
    uint32_t getIndex(Entry& e) { return next(e)._previous; }
    Entry& get(const EntryRef& e) { return _entries[e._entryIndex]; }

public:
    LruOrder(uint32_t size, RemoveFunctor& rf)
        : _removeFunctor(rf), _entries(size + 2)
    {
        initializeOrderVector();
    }

    /**
     * Clear all entries. Invalidates all previously returned entry references.
     */
    void clear() {
        for (uint32_t i=2, n=_entries.size(); i<n; ++i) {
            _entries[i]._inUse = false;
        }
    }

    /**
     * Adds a value to the order index. Return a reference to an object that
     * can be used to refer to the value.
     */
    EntryRef add(const Value& k) {
        Entry& e(previous(last()));
        if (e._inUse) {
            _removeFunctor.removedFromOrder(e._value);
        }
        e._value = k;
        e._inUse = true;
        moveToStart(EntryRef(getIndex(e)));
        return EntryRef(getIndex(e));
    }

    void remove(EntryRef ref) {
        uint32_t index(ref._entryIndex);
        Entry& e(get(ref));
        e._inUse = false;
            // Remove entry from current place in sequence
        next(e)._previous = e._previous;
        previous(e)._next = e._next;
            // Make entry fit being at end.
        e._next = 1;
        e._previous = last()._previous;
            // Insert at end
        previous(last())._next = index;
        last()._previous = index;
    }

    /**
     * Move given entry to the start of the order.
     */
    void moveToStart(EntryRef ref) {
        uint32_t index(ref._entryIndex);
        Entry& e(get(ref));
            // Remove entry from current place in sequence
        next(e)._previous = e._previous;
        previous(e)._next = e._next;
            // Make entry fit being at start.
        e._next = first()._next;
        e._previous = 0;
            // Insert at beginning
        next(first())._previous = index;
        first()._next = index;
    }

private:
    void initializeOrderVector() {
        if (_entries.size() < 1 || _entries.size() > 0xffffffffu - 3) {
            throw vespalib::IllegalArgumentException(
                    "LruOrder size needs to be between 1 and 3 below max "
                    "uint32_t value, as it needs to reserve 3 values.",
                    VESPA_STRLOC);
        }
        _entries[0]._next = 2; // First token
        _entries[1]._previous = _entries.size() - 1; // Last token
        for (uint32_t i=2, n=_entries.size(); i<n; ++i) {
            _entries[i]._next = i+1;
            _entries[i]._previous = i-1;
        }
            // Make first and last actual elements point to first and last.
        _entries[2]._previous = 0;
        _entries[_entries.size() - 1]._next = 1;
    }

    void print(std::ostream& out, bool verbose, const std::string& indent) const
    {
        if (verbose) {
            out << "LruOrder(" << (staticSize ? "static" : "dynamic") << "size "
                << (_entries.size() - 2) << ") {";
            for (uint32_t i=0; i<_entries.size(); ++i) {
                const Entry& e(_entries[i]);
                out << "\n" << indent << "  " << i << ": <- " << e._previous
                    << " " << e._next << " -> ";
                if (e._inUse) {
                    out << "(" << e._value << ")";
                }
            }
            out << "\n" << indent << "}";
        } else {
            out << "[";
            const Entry* e = &_entries[_entries[0]._next];
            if (e->_inUse) {
                out << e->_value;
            }
            while (true) {
                e = &_entries[e->_next];
                if (!e->_inUse) break;
                out << ", " << e->_value;
            }
            out << "]";
        }
    }
};

} // lib
} // storage
