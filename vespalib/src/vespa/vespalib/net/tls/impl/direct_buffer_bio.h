// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/crypto/openssl_typedefs.h>
#include <openssl/bio.h>

/*
 * Custom BIO implementations which offer direct write/read only buffer
 * access to underlying memory buffers. This removes the need to allocate
 * separate memory BIOs into/from which data is redundantly copied.
 *
 * These BIOs are merely views into buffers that the user must set appropriately
 * before invoking OpenSSL functions that invoke them. The ability to set buffers
 * is only available via scoped guards that cannot be copied or moved.
 *
 * Since no buffer allocation is ever done by these BIOs, it is the responsibility
 * of the caller to provide sufficiently large buffers that OpenSSL operations can
 * make progress.
 *
 * The BIOs ensure that OpenSSL cannot write to read-only buffers and vice versa.
 */

namespace vespalib::net::tls::impl {

crypto::BioPtr new_mutable_direct_buffer_bio();
crypto::BioPtr new_const_direct_buffer_bio();

struct MutableBufferView {
    // Could use a pointer pair instead (or just modify the ptr), but being explicit is good for readability.
    char* buffer;
    size_t size;
    size_t pos;
    size_t rpos;

    // Pending means "how much is written"
    size_t pending() const noexcept {
        return pos;
    }
};

struct ConstBufferView {
    const char* buffer;
    size_t size;
    size_t pos;

    // Pending means "how much is left to read"
    size_t pending() const noexcept {
        return size - pos;
    }
};

class ConstBufferViewGuard {
    ::BIO& _bio;
    ConstBufferView _view;
public:
    // Important: buffer view pointer and the buffer it points to MUST be
    // valid until unset_bio_buffer_view is called! Exception to the latter is
    // if the data buffer length is 0 AND the data buffer pointer is nullptr.
    // Precondition: bio must have been created by a call to new_const_direct_buffer_bio()
    ConstBufferViewGuard(::BIO& bio, const char* buffer, size_t sz) noexcept;
    ~ConstBufferViewGuard();

    // The current active buffer view has a reference into our own struct, so
    // we cannot allow that pointer to be invalidated by copies or moves.
    ConstBufferViewGuard(const ConstBufferViewGuard&) = delete;
    ConstBufferViewGuard& operator=(const ConstBufferViewGuard&) = delete;
    ConstBufferViewGuard(ConstBufferViewGuard&&) = delete;
    ConstBufferViewGuard& operator=(ConstBufferViewGuard&&) = delete;
};

class MutableBufferViewGuard {
    ::BIO& _bio;
    MutableBufferView _view;
public:
    // Important: buffer view pointer and the buffer it points to MUST be
    // valid until unset_bio_buffer_view is called! Exception to the latter is
    // if the data buffer length is 0 AND the data buffer pointer is nullptr.
    // Precondition: bio must have been created by a call to new_mutable_direct_buffer_bio()
    MutableBufferViewGuard(::BIO& bio, char* buffer, size_t sz) noexcept;
    ~MutableBufferViewGuard();

    // The current active buffer view has a reference into our own struct, so
    // we cannot allow that pointer to be invalidated by copies or moves.
    MutableBufferViewGuard(const MutableBufferViewGuard&) = delete;
    MutableBufferViewGuard& operator=(const MutableBufferViewGuard&) = delete;
    MutableBufferViewGuard(MutableBufferViewGuard&&) = delete;
    MutableBufferViewGuard& operator=(MutableBufferViewGuard&&) = delete;
};

}
