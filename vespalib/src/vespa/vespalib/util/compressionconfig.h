// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cmath>
#include <cstdint>
#include <cstddef>
#include <cstring>

namespace vespalib::compression {

struct CompressionConfig {
    enum Type : uint8_t {
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
        : CompressionConfig(NONE, 0, 90) {}
    CompressionConfig(Type t) noexcept
        : CompressionConfig(t, 9, 90) {}

    CompressionConfig(Type t, uint8_t level, uint8_t minRes) noexcept
        : CompressionConfig(t, level, minRes, 0) {}

    CompressionConfig(Type t, uint8_t lvl, uint8_t minRes, size_t minSz) noexcept
        : minSize(minSz), type(t), compressionLevel(lvl), threshold(minRes) {}

    bool operator==(const CompressionConfig& o) const noexcept {
        return (type == o.type
                && compressionLevel == o.compressionLevel
                && threshold == o.threshold);
    }
    bool operator!=(const CompressionConfig& o) const noexcept {
        return !operator==(o);
    }

    static Type toType(uint32_t val) noexcept {
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
    static Type toType(const char * val) noexcept {
        if (strncasecmp(val, "lz4", 3) == 0) {
            return LZ4;
        } if (strncasecmp(val, "zstd", 4) == 0) {
            return ZSTD;
        }
        return NONE;
    }
    static bool isCompressed(Type type) noexcept {
        return (type != CompressionConfig::NONE &&
                type != CompressionConfig::UNCOMPRESSABLE);
    }
    bool useCompression() const noexcept { return isCompressed(type); }

    uint32_t minSize;
    Type     type;
    uint8_t  compressionLevel;
    uint8_t  threshold;
};

}


