// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/compressionconfig.h>
#include <vespa/vespalib/util/memory.h>
#include <atomic>
#include <string>
#include <vector>

class FNET_DataBuffer;

namespace search::fs4transport {

using std::string;

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
    using StringRef = std::pair<uint32_t, uint32_t>;
    using Entry = std::pair<StringRef, StringRef>;
    using KeyValueVector = std::vector<Entry>;

    KeyValueVector   _entries;
    std::string _name;
    std::string _backing;
    const char * c_str(size_t sz) const { return _backing.c_str() + sz; }
    void set(StringRef & e, std::string_view s);
    void allocEntries(uint32_t cnt);
public:
    FS4Properties(FS4Properties &&) noexcept;
    FS4Properties &operator=(FS4Properties &&) noexcept;
    FS4Properties(const FS4Properties &) = delete;
    FS4Properties &operator=(const FS4Properties &) = delete;

    FS4Properties();
    ~FS4Properties();
    void setName(const char *name, uint32_t nameSize) { _name.assign(name, nameSize); }
    void setName(std::string_view val) {
        setName(val.data(), val.size());
    }
    void setKey(uint32_t entry, const char *key, uint32_t keySize);
    void setKey(uint32_t entry, std::string_view val) {
        setKey(entry, val.data(), val.size());
    }
    void setValue(uint32_t entry, const char *value, uint32_t valueSize);
    void setValue(uint32_t entry, std::string_view val) {
        setValue(entry, val.data(), val.size());
    }
    uint32_t size() const noexcept { return _entries.size(); }
    const std::string & name() const noexcept { return _name; }
    std::string_view key(uint32_t entry) const noexcept;
    std::string_view value(uint32_t entry) const noexcept;

    // sub-packet methods below
    uint32_t getLength() const noexcept;

    void encode(FNET_DataBuffer &dst);
    bool decode(FNET_DataBuffer &src, uint32_t &len);
    std::string toString(uint32_t indent = 0) const;
};

}
