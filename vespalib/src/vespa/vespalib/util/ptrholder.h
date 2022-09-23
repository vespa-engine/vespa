// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <algorithm>
#include <memory>
#include <mutex>

namespace vespalib {

/**
 * @brief A PtrHolder tracks a shared resource that can change
 *
 * A PtrHolder is typically a semi-global object where different
 * threads can obtain a std::shared_ptr to the current object representing a
 * shared resource that can change over time. The PtrHolder class is
 * especially useful when updates to the shared resource comes at
 * unexpected times. The reason for this is that a PtrHolder contains
 * two pointers; the current one and the new one. Updating the pointer
 * to the new version of the shared resource and actually making that
 * version the current version are two separate operations. This
 * enables you to latch in new versions when it fits best or even
 * ignore new versions all together. This class was originally
 * designed to keep track of config objects.
 **/
template <typename T>
class PtrHolder
{
private:
    std::shared_ptr<T>  _current;
    std::shared_ptr<T>  _next;
    mutable std::mutex  _lock;
    using LockGuard = std::lock_guard<std::mutex>;
public:
    PtrHolder(const PtrHolder &) = delete;
    PtrHolder &operator=(const PtrHolder &) = delete;
    /**
     * @brief Create an empty PtrHolder with both current and new
     * pointers set to 0
     **/
    PtrHolder() : _current(), _next(), _lock() {}

    /**
     * @brief Empty destructor
     *
     * std::shared_ptr instances are used internally to track shared
     * resources
     **/
    virtual ~PtrHolder();

    /**
     * @brief Check if the current value is set (not 0)
     *
     * @return true if the current value is set (not 0)
     **/
    bool hasValue() const { return bool(_current); }

    /**
     * @brief Check if the new value is set (not 0)
     *
     * @return true if the new value is set (not 0)
     **/
    bool hasNewValue() const { return bool(_next); }

    /**
     * @brief Set a new value
     *
     * Note that if no current value is set, this method will set the
     * current value instead of the new one.
     *
     * @param obj the new value
     **/
    void set(T *obj) {
        std::shared_ptr<T> tmp;
        {
            LockGuard guard(_lock);
            swap(tmp, _next);
            _next.reset(obj);
            if (!hasValue()) {
                swap(_current, _next);
            }
        }
    }

    /**
     * @brief Obtain the current value
     *
     * @return the current value
     **/
    std::shared_ptr<T> get() const {
        LockGuard guard(_lock);
        return std::shared_ptr<T>(_current);
    }

    /**
     * @brief Make the new value the current one
     *
     * @return false if there was no new value
     **/
    bool latch() {
        std::shared_ptr<T> tmp;
        {
            LockGuard guard(_lock);
            if (!hasNewValue()) {
                return false;
            }
            swap(tmp, _current);
            swap(_current, _next);
        }
        return true;
    }

    /**
     * @brief Discard both the current and the new value
     **/
    void clear() {
        std::shared_ptr<T> tmp1;
        std::shared_ptr<T> tmp2;
        {
            LockGuard guard(_lock);
            swap(tmp1, _current);
            swap(tmp2, _next);
        }
    }
};

template<typename T>
PtrHolder<T>::~PtrHolder() = default;

} // namespace vespalib
