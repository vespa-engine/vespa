// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cmath>
#include <cstdint>
#include <cstddef>
#include <cstring>

namespace vespalib::compression {

struct CompressionConfig {
    enum Type {
        NONE = 0,
        NONE_MULTI = 1,
        HISTORIC_2 = 2,
        HISTORIC_3 = 3,
        HISTORIC_4 = 4,
        UNCOMPRESSABLE = 5,
        LZ4 = 6,
        ZSTD = 7
    };

    CompressionConfig() noexcept
        : type(NONE), compressionLevel(0), threshold(90), minSize(0) {}
    CompressionConfig(Type t) noexcept
        : type(t), compressionLevel(9), threshold(90), minSize(0) {}

    CompressionConfig(Type t, uint8_t level, uint8_t minRes) noexcept
        : type(t), compressionLevel(level), threshold(minRes), minSize(0) {}

    CompressionConfig(Type t, uint8_t lvl, uint8_t minRes, size_t minSz) noexcept
        : type(t), compressionLevel(lvl), threshold(minRes), minSize(minSz) {}

    bool operator==(const CompressionConfig& o) const {
        return (type == o.type
                && compressionLevel == o.compressionLevel
                && threshold == o.threshold);
    }
    bool operator!=(const CompressionConfig& o) const {
        return !operator==(o);
    }

    static Type toType(uint32_t val) {
        switch (val) {
        case 1: return NONE_MULTI;
        case 2: return HISTORIC_2;
        case 3: return HISTORIC_3;
        case 4: return HISTORIC_4;
        case 5: return UNCOMPRESSABLE;
        case 6: return LZ4;
        case 7: return ZSTD;
        default: return NONE;
        }
    }
    static Type toType(const char * val) {
        if (strncasecmp(val, "lz4", 3) == 0) {
            return LZ4;
        } if (strncasecmp(val, "zstd", 4) == 0) {
            return ZSTD;
        }
        return NONE;
    }
    static bool isCompressed(Type type) {
        return (type != CompressionConfig::NONE &&
                type != CompressionConfig::UNCOMPRESSABLE);
    }
    bool useCompression() const { return isCompressed(type); }

    Type type;
    uint8_t compressionLevel;
    uint8_t threshold;
    size_t minSize;
};

class CompressionInfo
{
public:
    CompressionInfo(size_t uncompressedSize, size_t compressedSize)
        : _uncompressedSize(uncompressedSize), _compressedSize(compressedSize) { }
    size_t getUncompressedSize() const { return _uncompressedSize; }
    size_t getCompressedSize()   const { return _compressedSize; }
    double getCompressionRatio() const { return _uncompressedSize/_compressedSize; }
private:
    size_t _uncompressedSize;
    size_t _compressedSize;
};

inline CompressionInfo operator + (const CompressionInfo & a, const CompressionInfo & b)
{
    return CompressionInfo(a.getUncompressedSize() + b.getUncompressedSize(), a.getCompressedSize() + b.getCompressedSize());
}

}


