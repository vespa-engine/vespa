// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "serializablearray.h"
#include <vespa/document/util/serializableexceptions.h>
#include <vespa/document/util/bytebuffer.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <algorithm>
#include <cassert>
#include <vespa/log/log.h>
LOG_SETUP(".document.serializable-array");

using std::vector;
using vespalib::make_string;

namespace document {

namespace serializablearray {

using BufferMapT = vespalib::hash_map<int, ByteBuffer>;

class BufferMap : public BufferMapT {
public:
    using BufferMapT::BufferMapT;
};

}

void
SerializableArray::set(EntryMap entries, ByteBuffer buffer)
{
    _entries = std::move(entries);
    _uncompSerData = std::move(buffer);
}

SerializableArray::SerializableArray() = default;
SerializableArray::SerializableArray(SerializableArray &&) noexcept = default;
SerializableArray& SerializableArray::operator=(SerializableArray &&) noexcept = default;
SerializableArray::~SerializableArray() = default;

namespace {

template <typename T>
T &
ensure(std::unique_ptr<T> &owned) {
    if (!owned) {
        owned = std::make_unique<T>();
    }
    return *owned;
}

}

SerializableArray::SerializableArray(const SerializableArray& rhs)
    : _entries(rhs._entries),
      _uncompSerData(rhs._uncompSerData)
{
    for (size_t i(0); i < _entries.size(); i++) {
        Entry & e(_entries[i]);
        if (e.hasBuffer()) {
            // Pointing to a buffer in the _owned structure.
            ByteBuffer buf(ByteBuffer::copyBuffer(e.getBuffer(&_uncompSerData), e.size()));
            e.setBuffer(buf.getBuffer());
            ensure(_owned)[e.id()] = std::move(buf);
        } else {
            // If not it is relative to the buffer _uncompSerData, and hence it is valid as is.
        }
    }
}

SerializableArray &
SerializableArray::operator=(const SerializableArray &rhs)
{
    if (this != &rhs) {
        *this = SerializableArray(rhs);
    }
    return *this;
}

void SerializableArray::clear()
{
    _entries.clear();
    _uncompSerData = ByteBuffer(nullptr, 0);
}

void
SerializableArray::set(int id, ByteBuffer buffer)
{
    Entry e(id, buffer.getRemaining(), buffer.getBuffer());
    assert(buffer.getRemaining() < 0x80000000ul);
    ensure(_owned)[id] = std::move(buffer);
    auto it = find(id);
    if (it == _entries.end()) {
        _entries.push_back(e);
    } else {
        *it = e;
    }
}

void SerializableArray::set(int id, const char* value, int len)
{
    set(id, ByteBuffer::copyBuffer(value,len));
}

SerializableArray::EntryMap::const_iterator
SerializableArray::find(int id) const
{
    return std::find_if(_entries.begin(), _entries.end(), [id](const auto& e){ return e.id() == id; });
}

SerializableArray::EntryMap::iterator
SerializableArray::find(int id)
{
    return std::find_if(_entries.begin(), _entries.end(), [id](const auto& e){ return e.id() == id; });
}

bool
SerializableArray::has(int id) const
{
    return (find(id) != _entries.end());
}

vespalib::ConstBufferRef
SerializableArray::get(int id) const
{
    vespalib::ConstBufferRef buf;
    auto found = find(id);

    if (found != _entries.end()) {
        const Entry& entry = *found;
        buf = vespalib::ConstBufferRef(entry.getBuffer(&_uncompSerData), entry.size());
    }

    return buf;
}

void
SerializableArray::clear(int id)
{
    auto it  = find(id);
    if (it != _entries.end()) {
        _entries.erase(it);
    }
}

const char *
SerializableArray::Entry::getBuffer(const ByteBuffer * readOnlyBuffer) const {
    return hasBuffer() ? _data._buffer : readOnlyBuffer->getBuffer() + getOffset();
}

} // document
