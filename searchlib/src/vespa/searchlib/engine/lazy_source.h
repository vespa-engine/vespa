// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace search::engine {

/**
 * A lazy source uses a decoder interface to delay decoding an
 * object. Decoding is typically done in another thread as well to
 * avoid slowing down the critical path (io event loop).
 **/
template <typename T>
class LazySource {
public:
    struct Decoder {
        virtual std::unique_ptr<T> decode() = 0;
        virtual ~Decoder() = default;
    };
private:
    mutable std::unique_ptr<T> _object;
    mutable std::unique_ptr<Decoder> _decoder;
    void lazy_decode() const {
        if (_decoder && !_object) {
            _object = _decoder->decode();
            _decoder.reset();
        }
    }
public:
    explicit LazySource(T *object) noexcept : _object(object), _decoder() {}
    LazySource(std::unique_ptr<T> object) noexcept : _object(std::move(object)), _decoder() {}
    LazySource(std::unique_ptr<Decoder> decoder) noexcept : _object(), _decoder(std::move(decoder)) {}
    LazySource(LazySource &&) = default;
    ~LazySource() {}

    LazySource &operator=(LazySource &&) = delete;
    LazySource(const LazySource &) = delete;
    LazySource &operator=(const LazySource &) = delete;

    const T *get() const {
        lazy_decode();
        return _object.get();
    }
    const T *operator->() const { return get(); }
    std::unique_ptr<T> release() {
        lazy_decode();
        return std::move(_object);
    }
};

}
