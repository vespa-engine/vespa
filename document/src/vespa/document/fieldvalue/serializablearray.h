// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::SerializableArray
 * \brief key/value array that can be serialized and deserialized efficiently.
 *
 * The SerializableArray class is optimized for doing multiple
 * serialize()/deserialize() without changing attributes. Once
 * an attribute is changed, serialization is much slower. This makes
 * sense, since a document travels between a lot of processes and
 * queues, where nothing happens except serialization and deserialization.
 *
 * It also supports multiple deserializations, where serializations
 * from multiple other arrays are merged into one array.
 * Attributes that overlap Get the last known value.
 */

#pragma once

#include <vespa/vespalib/util/buffer.h>
#include <vespa/document/util/bytebuffer.h>
#include <vector>

#define VESPA_DLL_LOCAL  __attribute__ ((visibility("hidden")))

namespace document {

namespace serializablearray {
    class BufferMap;
}

class SerializableArray
{
public:
    /**
     * Contains the id of a field, the size and a buffer reference that is either
     * a relative offset to a common buffer, or the buffer itself it it is not.
     * The most significant bit of the _sz member indicates which of the 2 it is.
     */
    class Entry {
    public:
        Entry() : _id(0), _sz(0), _data() {}
        Entry(int i) : _id(i), _sz(0), _data()  {}
        Entry(uint32_t i, uint32_t sz, uint32_t off) : _id(i), _sz(sz), _data(off) {}
        Entry(uint32_t i, uint32_t sz, const char * buf) : _id(i), _sz(sz | BUFFER_MASK), _data(buf) {}

        int32_t id() const { return _id; }
        uint32_t size() const { return _sz & ~BUFFER_MASK; }
        bool hasBuffer() const { return (_sz & BUFFER_MASK); }
        bool operator < (const Entry & e) const { return cmp(e) < 0; }
        int cmp(const Entry & e) const { return _id - e._id; }
        void setBuffer(const char * buffer) { _data._buffer = buffer; _sz |= BUFFER_MASK; }
        VESPA_DLL_LOCAL const char * getBuffer(const ByteBuffer * readOnlyBuffer) const;
    private:
        uint32_t getOffset() const { return _data._offset; }
        enum { BUFFER_MASK=0x80000000 };
        int32_t      _id;
        uint32_t     _sz;
        union Data {
           Data() : _buffer(0) { }
           Data(const char * buffer) : _buffer(buffer) { }
           Data(uint32_t offset) : _offset(offset) { }
           const char * _buffer;
           uint32_t     _offset;
        } _data;
    };
    class EntryMap : public std::vector<Entry>
    {
    private:
        using V=std::vector<Entry>;
    public:
        EntryMap() : V() { }
    };

    static const uint32_t ReservedId = 100;
    static const uint32_t ReservedIdUpper = 128;

    using UP = std::unique_ptr<SerializableArray>;

    SerializableArray();
    SerializableArray(const SerializableArray&);
    SerializableArray& operator=(const SerializableArray&);
    SerializableArray(SerializableArray &&) noexcept;
    SerializableArray& operator=(SerializableArray &&) noexcept;
    ~SerializableArray();

    void set(EntryMap entries, ByteBuffer buffer);
    /**
     * Stores a value in the array.
     *
     * @param id The ID to associate the value with.
     * @param value The value to store.
     * @param len The length of the buffer.
     */
    void set(int id, const char* value, int len);

    /** Stores a value in the array. */
    void set(int id, ByteBuffer buffer);

    /**
     * Gets a value from the array. This is the faster version of the above.
     * It will just give you the pointers needed. No refcounting or anything.
     *
     * @param id The ID of the value to Get.
     *
     * @return Returns a reference to a buffer. c_str and size will be zero if
     * none is found.
     */
    vespalib::ConstBufferRef get(int id) const;

    /** @return Returns true if the given ID is Set in the array. */
    bool has(int id) const;

    /**
     * clears an attribute.
     *
     * @param id The ID of the attribute to remove from the array.
     */
    void clear(int id);

    /** Deletes all stored attributes. */
    void clear();

    bool empty() const { return _entries.empty(); }

    const ByteBuffer* getSerializedBuffer() const {
        return &_uncompSerData;
    }

    const EntryMap & getEntries() const { return _entries; }
private:
    /** Contains the stored attributes, with reference to the real data.. */
    EntryMap                  _entries;
    /** Data we deserialized from, if applicable. */
    ByteBuffer                _uncompSerData;
    std::unique_ptr<serializablearray::BufferMap> _owned;

    VESPA_DLL_LOCAL EntryMap::const_iterator find(int id) const;
    VESPA_DLL_LOCAL EntryMap::iterator find(int id);
};

} // document
