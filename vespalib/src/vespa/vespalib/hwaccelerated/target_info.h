// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <string>
#include <string_view>

namespace vespalib::hwaccelerated {

// Information that identifies a particular CPU vectorization target
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
    // Returns a static string representing what implementation was used to
    // create the vectorization target. Currently;
    //  - "AutoVec" - auto-vectorized kernel
    //  - "Highway" - explicitly vectorized kernel via Google Highway
    [[nodiscard]] constexpr const char* implementation_name() const noexcept {
        return _implementation_name;
    }
    // Returns a static string representing the name of the underlying accelerator
    // target (e.g. "AVX3", "NEON" etc.). Target names may be non-unique across
    // different implementations.
    [[nodiscard]] constexpr const char* target_name() const noexcept {
        return _target_name;
    }
    [[nodiscard]] constexpr uint16_t vector_width_bytes() const noexcept {
        return _vector_width_bytes;
    }
    [[nodiscard]] constexpr uint16_t vector_width_bits() const noexcept {
        return _vector_width_bytes * 8;
    }

    [[nodiscard]] constexpr bool operator==(const TargetInfo& rhs) const noexcept {
        return (std::string_view{_implementation_name} == rhs._implementation_name &&
                std::string_view{_target_name} == rhs._target_name &&
                _vector_width_bytes == rhs._vector_width_bytes);
    }

    [[nodiscard]] std::string to_string() const;
};

} // vespalib::hwaccelerated
