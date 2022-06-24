// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstring>

namespace fsa {

template <typename T>
class Unaligned {
private:
    char _data[sizeof(T)];

public:
    Unaligned() = delete;
    Unaligned(const Unaligned &) = delete;
    Unaligned(Unaligned &&) = delete;

    Unaligned &operator=(const Unaligned &) = default;
    Unaligned &operator=(Unaligned &&) = default;

    static_assert(std::is_trivial_v<T>);
    static_assert(alignof(T) > 1, "value is always aligned");

    constexpr static Unaligned &at(void *p) noexcept {
        return *reinterpret_cast<Unaligned*>(p);
    }
    constexpr static const Unaligned &at(const void *p) noexcept {
        return *reinterpret_cast<const Unaligned*>(p);
    }

    constexpr static Unaligned *ptr(void *p) noexcept {
        return reinterpret_cast<Unaligned*>(p);
    }
    constexpr static const Unaligned *ptr(const void *p) noexcept {
        return reinterpret_cast<const Unaligned*>(p);
    }

    T read() const noexcept {
        T value;
        static_assert(sizeof(_data) == sizeof(value));
        memcpy(&value, _data, sizeof(value));
        return value;
    }
    void write(const T &value) noexcept {
        static_assert(sizeof(_data) == sizeof(value));
        memcpy(_data, &value, sizeof(value));
    }
    operator T () const noexcept { return read(); }
    Unaligned &operator=(const T &value) noexcept {
        write(value);
        return *this;
    }
};

}
