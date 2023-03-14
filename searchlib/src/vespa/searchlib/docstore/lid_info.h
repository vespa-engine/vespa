// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/generationhandler.h>
#include <limits>
#include <vector>
#include <mutex>

namespace search {

class LidInfo {
public:
    LidInfo() noexcept : _value() { }
    LidInfo(uint64_t rep) noexcept { _value.r = rep; }
    LidInfo(uint32_t fileId, uint32_t chunkId, uint32_t size);
    uint32_t getFileId()  const noexcept { return _value.v.fileId; }
    uint32_t getChunkId() const noexcept { return _value.v.chunkId; }
    uint32_t size()       const noexcept { return _value.v.size << SIZE_SHIFT; }
    operator uint64_t ()  const noexcept { return _value.r; }
    bool empty()          const noexcept { return size() == 0; }
    bool valid() const noexcept { return _value.r != std::numeric_limits<uint64_t>::max(); }

    bool operator==(const LidInfo &b) const noexcept {
        return (getFileId() == b.getFileId()) &&
               (getChunkId() == b.getChunkId());
    }
    bool operator < (const LidInfo &b) const noexcept {
        return (getFileId() == b.getFileId())
                   ? (getChunkId() < b.getChunkId())
                   : (getFileId() < b.getFileId());
    }
    static constexpr uint32_t getFileIdLimit() { return 1 << NUM_FILE_BITS; }
    static constexpr uint32_t getChunkIdLimit() { return 1 << NUM_CHUNK_BITS; }
private:
    static constexpr uint32_t computeAlignedSize(uint32_t sz) {
        return (sz+((1<<SIZE_SHIFT)-1)) >> SIZE_SHIFT;
    }
    static constexpr uint32_t getSizeLimit() {
        return std::numeric_limits<uint32_t>::max() - ((2<<SIZE_SHIFT)-2);
    }
    static constexpr uint32_t NUM_FILE_BITS = 16;
    static constexpr uint32_t NUM_CHUNK_BITS = 22;
    static constexpr uint32_t NUM_SIZE_BITS = 26;
    static constexpr uint32_t SIZE_SHIFT = 32 - NUM_SIZE_BITS;
    struct Rep {
        uint64_t fileId : NUM_FILE_BITS;
        uint64_t chunkId : NUM_CHUNK_BITS;
        uint64_t size : NUM_SIZE_BITS;
    };
    union Value {
        Value() : r(std::numeric_limits<uint64_t>::max()) { }
        Rep v;
        uint64_t r;
    } _value;
};

class LidInfoWithLid : public LidInfo {
public:
    LidInfoWithLid(LidInfo lidInfo, uint32_t lid) noexcept : LidInfo(lidInfo), _lid(lid) { }
    uint32_t getLid() const { return _lid; }
private:
    uint32_t _lid;
};

using LidInfoWithLidV = std::vector<LidInfoWithLid>;

class ISetLid
{
public:
    using unique_lock = std::unique_lock<std::mutex>;
    virtual ~ISetLid() = default;
    virtual void setLid(const unique_lock & guard, uint32_t lid, const LidInfo & lm) = 0;
};

class IGetLid
{
public:
    using Guard = vespalib::GenerationHandler::Guard;
    using unique_lock = std::unique_lock<std::mutex>;
    virtual ~IGetLid() = default;

    virtual LidInfo getLid(const Guard & guard, uint32_t lid) const = 0;
    virtual unique_lock getLidGuard(uint32_t lid) const = 0;
    virtual Guard getLidReadGuard() const = 0;
};

}
