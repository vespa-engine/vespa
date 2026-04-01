// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <array>
#include <cassert>
#include <concepts>
#include <cstddef>
#include <cstdint>
#include <memory>
#include <span>
#include <utility>

// Typed Data Layout
//
// Pre-plan which objects are needed and pack them into memory,
// vectorized by type and stored in a single contiguous memory block.
//
// Domain:
//   Defines the set of allowed types. There must be at least one.
//
// Layout:
//   Used to plan which objects are needed.
//   Reserves handles that can later be resolved against Data.
//
// Data:
//   Owns the packed storage for all objects planned by Layout.
//
// Handle:
//   Reserved by Layout for a single object.
//   Resolved against Data to access the object.
//
// ArrayHandle:
//   Like Handle, but for multiple objects of the same type. An
//   ArrayHandle can be resolved into a span of values or decomposed
//   into individual Handles.

namespace vespalib::tdl {

template <size_t align>
constexpr size_t align_up(size_t value) {
    static_assert(align != 0 && (align & (align - 1)) == 0);
    return (value + (align - 1)) & ~(align - 1);
}

template <typename H, typename... Ts>
constexpr bool has_duplicate_types() {
    if constexpr (sizeof...(Ts) == 0) {
        return false;
    } else {
        static constexpr bool found_h = (std::same_as<H, Ts> || ...);
        return found_h || has_duplicate_types<Ts...>();
    }
}

template <typename T>
constexpr size_t get_type_id() {
    static_assert(false, "get_type_id: type not found");
    return 0;
}
template <typename T, typename H, typename... Ts>
constexpr size_t get_type_id() {
    if constexpr (std::same_as<T,H>) {
        return 0;
    } else {
        return 1 + get_type_id<T, Ts...>();
    }
}

template <size_t I>
constexpr auto get_type_at() {
    static_assert(false, "get_type_at: index too large");
    return std::type_identity<int>{};
}
template <size_t I, typename H, typename... Ts>
constexpr auto get_type_at() {
    if constexpr (I == 0) {
        return std::type_identity<H>{};
    } else {
        return get_type_at<I - 1, Ts...>();
    }
}

template <typename H, typename... Ts>
struct Domain {
    static constexpr size_t num_types = 1 + sizeof...(Ts);
    using index_sequence = std::index_sequence_for<H, Ts...>;
    static_assert(!has_duplicate_types<H, Ts...>(), "duplicate types not allowed");
    template <typename T> static constexpr size_t type_id = get_type_id<T, H, Ts...>();
    template <size_t I> using type_at = decltype(get_type_at<I, H, Ts...>())::type;
    static constexpr size_t max_align = std::max({alignof(H), alignof(Ts)...});
};

template <typename T>
struct is_domain : std::false_type {};

template <typename... Ts>
struct is_domain<Domain<Ts...>> : std::true_type {};

template <typename T>
concept domain = is_domain<T>::value;

template <typename T, domain D>
constexpr size_t type_id() {
    return D::template type_id<T>;
}

template <domain D> class Data;
template <domain D> class DataDeleter;
template <domain D> class Layout;
class ArrayHandle;

class Handle {
private:
    friend class ArrayHandle;
    friend class HandleIterator;
    template <domain D> friend class Layout;
    uint32_t _value;
    static constexpr uint32_t invalid_handle = 0xffffffff;
    static constexpr uint32_t offset_bits    = 24;
    static constexpr uint32_t offset_mask    = 0xffffff;
    static constexpr uint32_t max_offset     = 0xfffffe; // keep space for iterator end
    static constexpr uint32_t type_mask      = 0xff;
    static constexpr uint32_t max_type       = 0xff;

    constexpr Handle(uint32_t value) noexcept : _value(value) {}
    template <size_t type>
    static constexpr Handle make(uint32_t offset) noexcept {
        static_assert(type <= max_type);
        assert(offset <= max_offset);
        return Handle((type << offset_bits) | offset);
    }
public:
    constexpr Handle() noexcept : _value(invalid_handle) {}
    constexpr bool valid() const noexcept { return _value != invalid_handle; }
    constexpr uint32_t type() const noexcept { return (_value >> offset_bits) & type_mask; }
    constexpr uint32_t offset() const noexcept { return _value & offset_mask; }
    constexpr bool operator==(Handle rhs) const noexcept { return _value == rhs._value; }
    constexpr bool operator<(Handle rhs) const noexcept { return _value < rhs._value; }
};

class HandleIterator {
private:
    Handle _handle;
    friend class ArrayHandle;
    constexpr explicit HandleIterator(uint32_t raw_handle) noexcept
      : _handle(raw_handle) {}
public:
    constexpr Handle operator*() const noexcept {
        return _handle;
    }
    constexpr HandleIterator& operator++() noexcept {
        ++_handle._value;
        return *this;
    }
    constexpr bool operator!=(HandleIterator rhs) const noexcept {
        return _handle._value != rhs._handle._value;
    }
};

class ArrayHandle {
private:
    template <domain D> friend class Layout;
    template <domain D> friend class Data;
    Handle _base;
    uint32_t _size;

    constexpr ArrayHandle(Handle base, uint32_t size) noexcept
      : _base(base), _size(size) {}
    template <size_t type>
    static constexpr ArrayHandle make(uint32_t offset, size_t size) noexcept {
        assert((size_t(offset) + size) <= (Handle::max_offset + 1));
        return ArrayHandle(Handle::make<type>(offset), size);
    }
public:
    constexpr ArrayHandle() noexcept : _base(), _size(0) {}
    constexpr bool valid() const noexcept { return _base.valid(); }
    constexpr size_t size() const noexcept { return _size; }
    constexpr bool empty() const noexcept { return _size == 0; }
    constexpr Handle at(size_t i) const noexcept {
        assert(i < _size);
        return {_base._value + uint32_t(i)};
    }
    constexpr auto begin() const noexcept { return HandleIterator(_base._value); }
    constexpr auto end() const noexcept { return HandleIterator(_base._value + _size); }
};

template <typename... Ts>
class Data<Domain<Ts...>> {
private:
    using MyDomain = Domain<Ts...>;
    friend class Layout<MyDomain>;
    friend class DataDeleter<MyDomain>;
    struct VectorRef {
        uint32_t pos;
        uint32_t len;
        constexpr VectorRef() noexcept : pos(0), len(0) {}
    };
    size_t _allocated;
    std::array<VectorRef, MyDomain::num_types> _header;
    constexpr Data(size_t need_size) noexcept : _allocated(need_size), _header{} {}
    Data(const Data &) = delete;
    Data(Data &&) = delete;
    Data& operator=(const Data&) = delete;
    Data& operator=(Data&&) = delete;
public:
    template <typename T>
    std::span<T> all_of() noexcept {
        static constexpr size_t I = type_id<T, MyDomain>();
        char *base = reinterpret_cast<char*>(this);
        return {reinterpret_cast<T*>(base + _header[I].pos), _header[I].len};
    }
    template <typename T>
    std::span<const T> all_of() const noexcept {
        static constexpr size_t I = type_id<T, MyDomain>();
        const char *base = reinterpret_cast<const char*>(this);
        return {reinterpret_cast<const T*>(base + _header[I].pos), _header[I].len};
    }
    template <typename T> const T& resolve(Handle h) const noexcept {
        static constexpr size_t I = type_id<T, MyDomain>();
        assert(h.type() == I);
        auto array = all_of<T>();
        size_t offset = h.offset();
        assert(offset < array.size());
        return array[offset];
    }
    template <typename T> T& resolve(Handle h) noexcept {
        return const_cast<T&>(std::as_const(*this).template resolve<T>(h));
    }
    template <typename T> std::span<const T> resolve_array(ArrayHandle ah) const noexcept {
        static constexpr size_t I = type_id<T, MyDomain>();
        assert(ah._base.type() == I);
        auto array = all_of<T>();
        size_t offset = ah._base.offset();
        assert(offset + ah.size() <= array.size());
        return array.subspan(offset, ah.size());
    }
    template <typename T> std::span<T> resolve_array(ArrayHandle ah) noexcept {
        auto c = std::as_const(*this).template resolve_array<T>(ah);
        return {const_cast<T*>(c.data()), c.size()};
    }
    constexpr size_t allocated() const noexcept { return _allocated; }
};

template <typename... Ts>
class DataDeleter<Domain<Ts...>> {
private:
    using MyDomain = Domain<Ts...>;
    using MyData = Data<MyDomain>;
public:
    void operator()(MyData *ptr) noexcept {
        auto destruct_array = [&]<typename T>() {
            for (auto &obj : ptr->template all_of<T>()) {
                obj.~T();
            }
        };
        (destruct_array.template operator()<Ts>(), ...);
        ::operator delete(ptr, std::align_val_t(MyDomain::max_align));
    }
};

template <domain D>
using DataUP = std::unique_ptr<Data<D>, DataDeleter<D>>;

template <typename... Ts>
class Layout<Domain<Ts...>> {
private:
    using MyDomain = Domain<Ts...>;
    std::array<uint32_t, MyDomain::num_types> counts{};
public:
    template <typename T>
    Handle reserve() {
        static constexpr size_t I = type_id<T, MyDomain>();
        return Handle::make<I>(counts[I]++);
    }
    template <typename T>
    ArrayHandle reserve_array(size_t size) {
        static constexpr size_t I = type_id<T, MyDomain>();
        auto start = counts[I];
        counts[I] += size;
        return ArrayHandle::make<I>(start, size);
    }
    template <typename T>
    ArrayHandle all_of() const {
        static constexpr size_t I = type_id<T, MyDomain>();
        return ArrayHandle::make<I>(0, counts[I]);
    }
    DataUP<MyDomain> create_data() const {
        using MyData = Data<MyDomain>;
        size_t need_size = sizeof(MyData);
        [&]<size_t... Is>(std::index_sequence<Is...>) {
            auto handle_array = [&]<typename T, size_t I>() {
                need_size = align_up<alignof(T)>(need_size);
                need_size += counts[I] * sizeof(T);
            };
            (handle_array.template operator()<Ts, Is>(), ...);
        }(typename MyDomain::index_sequence{});
        assert(need_size <= UINT32_MAX);
        char *mem = static_cast<char *>(::operator new(need_size, std::align_val_t(MyDomain::max_align)));
        DataUP<MyDomain> result(new (mem) MyData(need_size));
        auto &obj = *result;
        size_t offset = sizeof(MyData);
        [&]<size_t... Is>(std::index_sequence<Is...>) {
            auto construct_array = [&]<typename T, size_t I>() {
                offset = align_up<alignof(T)>(offset);
                obj._header[I].pos = uint32_t(offset);
                T *arr = reinterpret_cast<T*>(mem + offset);
                for (uint32_t j = 0; j < counts[I]; ++j) {
                    new (arr + j) T();
                    ++obj._header[I].len;
                }
                offset += (counts[I] * sizeof(T));
            };
            (construct_array.template operator()<Ts, Is>(), ...);
        }(typename MyDomain::index_sequence{});
        assert(offset == need_size);
        return result;
    }
};

} // vespalib::tdl
