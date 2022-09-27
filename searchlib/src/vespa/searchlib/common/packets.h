// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/compressionconfig.h>
#include <vespa/vespalib/util/memory.h>
#include <vector>
#include <atomic>

class FNET_DataBuffer;

namespace search::fs4transport {

using vespalib::string;

class FS4PersistentPacketStreamer {
    using CompressionConfig = vespalib::compression::CompressionConfig;

    std::atomic<unsigned int> _compressionLimit;
    std::atomic<unsigned int> _compressionLevel;
    std::atomic<CompressionConfig::Type> _compressionType;

public:
    static FS4PersistentPacketStreamer Instance;

    FS4PersistentPacketStreamer();

    void SetCompressionLimit(unsigned int limit) { _compressionLimit.store(limit, std::memory_order_relaxed); }
    void SetCompressionLevel(unsigned int level) { _compressionLevel.store(level, std::memory_order_relaxed); }
    void SetCompressionType(CompressionConfig::Type compressionType) { _compressionType.store(compressionType, std::memory_order_relaxed); }
    CompressionConfig::Type getCompressionType() const { return _compressionType.load(std::memory_order_relaxed); }
    uint32_t getCompressionLimit() const { return _compressionLimit.load(std::memory_order_relaxed); }
    uint32_t getCompressionLevel() const { return _compressionLevel.load(std::memory_order_relaxed); }
};

//==========================================================================

class FS4Properties
{
private:
    typedef std::pair<uint32_t, uint32_t> StringRef;
    typedef std::pair<StringRef, StringRef> Entry;
    typedef std::vector<Entry> KeyValueVector;

    KeyValueVector   _entries;
    vespalib::string _name;
    vespalib::string _backing;
    const char * c_str(size_t sz) const { return _backing.c_str() + sz; }
    void set(StringRef & e, vespalib::stringref s);
    void allocEntries(uint32_t cnt);
public:
    FS4Properties(FS4Properties &&);
    FS4Properties &operator=(FS4Properties &&);

    FS4Properties();
    ~FS4Properties();
    void setName(const char *name, uint32_t nameSize) { _name.assign(name, nameSize); }
    void setName(vespalib::stringref val) {
        setName(val.data(), val.size());
    }
    void setKey(uint32_t entry, const char *key, uint32_t keySize);
    void setKey(uint32_t entry, vespalib::stringref val) {
        setKey(entry, val.data(), val.size());
    }
    void setValue(uint32_t entry, const char *value, uint32_t valueSize);
    void setValue(uint32_t entry, vespalib::stringref val) {
        setValue(entry, val.data(), val.size());
    }
    uint32_t size() const { return _entries.size(); }
    const char *getName() const { return _name.c_str(); }
    uint32_t getNameLen() const { return _name.size(); }
    const char *getKey(uint32_t entry) const { return c_str(_entries[entry].first.first); }
    uint32_t getKeyLen(uint32_t entry) const { return _entries[entry].first.second; }
    const char *getValue(uint32_t entry) const { return c_str(_entries[entry].second.first); }
    uint32_t getValueLen(uint32_t entry) const { return _entries[entry].second.second; }

    // sub-packet methods below
    uint32_t getLength();

    void encode(FNET_DataBuffer &dst);
    bool decode(FNET_DataBuffer &src, uint32_t &len);
    vespalib::string toString(uint32_t indent = 0) const;
};

}
