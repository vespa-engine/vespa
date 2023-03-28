// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <atomic>
#include <concepts>
#include <memory>
#include <utility>

// This file contains code that implements intrusive reference
// counting with smart handles (ref_counted<T> points to T, T inherits
// enable_ref_counted).

// Functions with names starting with 'internal' are not intended for
// direct use, but are available for completeness (enables incremental
// re-write of code from bald to smart pointers).

namespace vespalib {

// This is the actual reference count. It cannot be moved or copied,
// but it can be changed even when const. Classes that want to be
// counted need to inherit from this. The destructor is virtual to
// make sure the right one is called. This also enables the subref
// function to directly delete the shared object.
class enable_ref_counted
{
    static constexpr uint32_t MAGIC = 0xcc56a933;
private:
    uint32_t _guard;
    mutable std::atomic<uint32_t> _refs;
protected:
    enable_ref_counted() noexcept : _guard(MAGIC), _refs(1) {}
public:
    virtual ~enable_ref_counted() noexcept;
    enable_ref_counted(enable_ref_counted &&) = delete;
    enable_ref_counted(const enable_ref_counted &) = delete;
    enable_ref_counted &operator=(enable_ref_counted &&) = delete;
    enable_ref_counted &operator=(const enable_ref_counted &) = delete;
    void internal_addref(uint32_t cnt) const noexcept;
    void internal_addref() const noexcept { internal_addref(1); }
    void internal_subref(uint32_t cnt, uint32_t reserve) const noexcept;
    void internal_subref() const noexcept { internal_subref(1, 0); }
    uint32_t count_refs() const noexcept;
};

// This is the handle to a shared object. The handle itself is not
// thread safe.
template <typename T>
class ref_counted
{
    // carefully placed here to give the best error messages
    static_assert(std::derived_from<T,enable_ref_counted>);
    template <typename X> friend class ref_counted;
private:
    T *_ptr;
    ref_counted(T *ptr) noexcept : _ptr(ptr) {
        // verify that ptr points to a valid object
        (void) ptr->count_refs();
    }
    void maybe_subref() noexcept {
        if (_ptr) [[likely]] {
            _ptr->internal_subref();
        }
    }
    static T *maybe_addref(T *ptr) noexcept {
        if (ptr) [[likely]] {
            ptr->internal_addref();
        }
        return ptr;
    }
    void replace_with(T *ptr) noexcept {
        maybe_subref();
        _ptr = ptr;
    }
public:
    ref_counted() noexcept : _ptr(nullptr) {}
    ref_counted(ref_counted &&rhs) noexcept : _ptr(std::exchange(rhs._ptr, nullptr)) {}
    ref_counted(const ref_counted &rhs) noexcept : _ptr(maybe_addref(rhs._ptr)) {}
    ref_counted &operator=(ref_counted &&rhs) noexcept {
        replace_with(std::exchange(rhs._ptr, nullptr));
        return *this;
    }
    ref_counted &operator=(const ref_counted &rhs) noexcept {
        replace_with(maybe_addref(rhs._ptr));
        return *this;
    }
    template <typename X> ref_counted(ref_counted<X> &&rhs) noexcept : _ptr(std::exchange(rhs._ptr, nullptr)) {}
    template <typename X> ref_counted(const ref_counted<X> &rhs) noexcept : _ptr(maybe_addref(rhs._ptr)) {}
    template <typename X> ref_counted &operator=(ref_counted<X> &&rhs) noexcept {
        replace_with(std::exchange(rhs._ptr, nullptr));
        return *this;
    }
    template <typename X> ref_counted &operator=(const ref_counted<X> &rhs) noexcept {
        replace_with(maybe_addref(rhs._ptr));
        return *this;
    }
    T *operator->() const noexcept { return _ptr; }
    T &operator*() const noexcept { return *_ptr; }
    operator bool() const noexcept { return (_ptr != nullptr); }
    void reset() noexcept { replace_with(nullptr); }
    ~ref_counted() noexcept { maybe_subref(); }
    // NB: will not call subref
    T *internal_detach() noexcept { return std::exchange(_ptr, nullptr); }
    // NB: will not call addref
    static ref_counted internal_attach(T *ptr) noexcept { return ref_counted(ptr); }
};

// similar to make_shared;
// create a reference counted object and return a handle to it
template <typename T, typename ... Args>
ref_counted<T> make_ref_counted(Args && ... args) {
    return ref_counted<T>::internal_attach(new T(std::forward<Args>(args)...));
}

// similar to shared_from_this;
// create a new handle to a reference counted object
// (NB: object must still be valid)
template <typename T>
ref_counted<T> ref_counted_from(T &t) noexcept {
    t.internal_addref();
    return ref_counted<T>::internal_attach(std::addressof(t));
}

}
