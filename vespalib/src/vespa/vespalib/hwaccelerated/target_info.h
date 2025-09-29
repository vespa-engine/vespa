// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <string>

namespace vespalib::hwaccelerated {

class TargetInfo {
    // All strings must have static lifetime
    const char* _implementation_name;
    const char* _target_name;
    uint16_t    _vector_width_bytes;
public:
    constexpr TargetInfo() noexcept
        : _implementation_name("Unknown"),
          _target_name("Unknown"),
          _vector_width_bytes(16)
    {}

    constexpr TargetInfo(const char* implementation_name,
                         const char* target_name,
                         uint16_t vector_width_bytes) noexcept
        : _implementation_name(implementation_name),
          _target_name(target_name),
          _vector_width_bytes(vector_width_bytes)
    {
    }

    [[nodiscard]] constexpr const char* implementation_name() const noexcept {
        return _implementation_name;
    }
    // Returns a static string representing the name of the underlying accelerator
    // target (e.g. "AVX3", "NEON" etc.)
    [[nodiscard]] constexpr const char* target_name() const noexcept {
        return _target_name;
    }
    [[nodiscard]] constexpr uint16_t vector_width_bytes() const noexcept {
        return _vector_width_bytes;
    }
    [[nodiscard]] constexpr uint16_t vector_width_bits() const noexcept {
        return _vector_width_bytes * 8;
    }

    [[nodiscard]] std::string to_string() const;
};

} // vespalib::hwaccelerated
