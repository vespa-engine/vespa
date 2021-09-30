// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "direct_buffer_bio.h"
#include <vespa/vespalib/crypto/crypto_exception.h>
#include <vespa/vespalib/util/backtrace.h>
#include <utility>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".vespalib.net.tls.impl.direct_buffer_bio");

/*
 * The official OpenSSL docs are basically devoid of information on how to write
 * your own BIOs, so most of the information used to implement our custom BIOs
 * is gleaned from other implementations and by reading the OpenSSL source code.
 *
 * Primary references used for implementation:
 *  - https://github.com/openssl/openssl/blob/master/crypto/bio/bss_mem.c
 *  - https://github.com/indutny/uv_ssl_t/blob/master/src/bio.c
 */

using namespace vespalib::crypto;

namespace vespalib::net::tls::impl {

namespace {

int buffer_bio_init(::BIO* bio);
int buffer_bio_destroy(::BIO* bio);
int mutable_buffer_bio_write(::BIO* bio, const char* src_buf, int len);
int const_buffer_bio_write(::BIO* bio, const char* src_buf, int len);
int mutable_buffer_bio_read(::BIO* bio, char* dest_buf, int len);
int const_buffer_bio_read(::BIO* bio, char* dest_buf, int len);
long mutable_buffer_bio_ctrl(::BIO* bio, int cmd, long num, void* ptr);
long const_buffer_bio_ctrl(::BIO* bio, int cmd, long num, void* ptr);

// How to wrangle BIOs and their methods is completely changed after OpenSSL 1.1
// For older versions, we must directly create a struct with callback fields set
// and can access the BIO fields directly. In 1.1 and beyond everything is hidden
// by indirection functions (these are _not_ available in prior versions).
#if (OPENSSL_VERSION_NUMBER < 0x10100000L)

#if !defined(BIO_TYPE_START)
#  define BIO_TYPE_START 128 // Constant hoisted from OpenSSL >= 1.1.0
#endif

const ::BIO_METHOD mutable_buf_method_instance = {
    (BIO_TYPE_START + 1) | BIO_TYPE_SOURCE_SINK, // BIO_TYPE_SOURCE_SINK sets high bits, not low bits, so no clobbering
    "mutable direct buffer access BIO",
    mutable_buffer_bio_write, // write func
    mutable_buffer_bio_read, // read func
    nullptr, // puts func
    nullptr, // gets func
    mutable_buffer_bio_ctrl, // ctrl func
    buffer_bio_init, // init func
    buffer_bio_destroy, // destroy func
    nullptr, // callback ctrl func
};

const ::BIO_METHOD const_buf_method_instance = {
    (BIO_TYPE_START + 2) | BIO_TYPE_SOURCE_SINK,
    "const direct buffer access BIO",
    const_buffer_bio_write, // write func
    const_buffer_bio_read, // read func
    nullptr, // puts func
    nullptr, // gets func
    const_buffer_bio_ctrl, // ctrl func
    buffer_bio_init, // init func
    buffer_bio_destroy, // destroy func
    nullptr, // callback ctrl func
};

struct BioMethodWrapper {
    const ::BIO_METHOD* method; // Global instance
    int type_index;
};

BioMethodWrapper mutable_buf_method() {
    return {&mutable_buf_method_instance, mutable_buf_method_instance.type};
}

BioMethodWrapper const_buf_method() {
    return {&const_buf_method_instance, const_buf_method_instance.type};
}

void set_bio_data(::BIO& bio, void* ptr) {
    bio.ptr = ptr;
}

void* get_bio_data(::BIO& bio) {
    return bio.ptr;
}

void set_bio_shutdown(::BIO& bio, int shutdown) {
    bio.shutdown = shutdown;
}

int get_bio_shutdown(::BIO& bio) {
    return bio.shutdown;
}

void set_bio_init(::BIO& bio, int init) {
    bio.init = init;
}

#else // OpenSSL 1.1 and beyond

struct BioMethodDeleter {
    void operator()(::BIO_METHOD* meth) const noexcept {
        ::BIO_meth_free(meth);
    }
};
using BioMethodPtr = std::unique_ptr<::BIO_METHOD, BioMethodDeleter>;

struct BioMethodWrapper {
    BioMethodPtr method;
    int type_index;
};

struct BioMethodParams {
    const char* bio_name;
    int (*bio_write)(::BIO*, const char*, int);
    int (*bio_read)(::BIO*, char*, int);
    long (*bio_ctrl)(::BIO*, int, long, void*);
};

BioMethodWrapper create_bio_method(const BioMethodParams& params) {
    int type_index = ::BIO_get_new_index() | BIO_TYPE_SOURCE_SINK;
    if (type_index == -1) {
        throw CryptoException("BIO_get_new_index");
    }
    BioMethodPtr bm(::BIO_meth_new(type_index, params.bio_name));
    if (!::BIO_meth_set_create(bm.get(), buffer_bio_init) ||
        !::BIO_meth_set_destroy(bm.get(), buffer_bio_destroy) ||
        !::BIO_meth_set_write(bm.get(), params.bio_write) ||
        !::BIO_meth_set_read(bm.get(), params.bio_read) ||
        !::BIO_meth_set_ctrl(bm.get(), params.bio_ctrl)) {
        throw CryptoException("Failed to set BIO_METHOD callback");
    }
    return {std::move(bm), type_index};
}

BioMethodWrapper create_mutable_bio_method() {
    return create_bio_method({"mutable direct buffer access BIO", mutable_buffer_bio_write,
                              mutable_buffer_bio_read, mutable_buffer_bio_ctrl});
}

BioMethodWrapper create_const_bio_method() {
    return create_bio_method({"const direct buffer access BIO", const_buffer_bio_write,
                              const_buffer_bio_read, const_buffer_bio_ctrl});
}

const BioMethodWrapper& mutable_buf_method() {
    static BioMethodWrapper wrapper = create_mutable_bio_method();
    return wrapper;
}

const BioMethodWrapper& const_buf_method() {
    static BioMethodWrapper wrapper = create_const_bio_method();
    return wrapper;
}

void set_bio_data(::BIO& bio, void* ptr) {
    ::BIO_set_data(&bio, ptr);
}

void set_bio_shutdown(::BIO& bio, int shutdown) {
    ::BIO_set_shutdown(&bio, shutdown);
}

int get_bio_shutdown(::BIO& bio) {
    return ::BIO_get_shutdown(&bio);
}

void set_bio_init(::BIO& bio, int init) {
    ::BIO_set_init(&bio, init);
}

void* get_bio_data(::BIO& bio) {
    return ::BIO_get_data(&bio);
}

#endif

BioPtr new_direct_buffer_bio(const ::BIO_METHOD& method) {
#if (OPENSSL_VERSION_NUMBER < 0x10100000L)
    auto* bio = ::BIO_new(const_cast<::BIO_METHOD*>(&method)); // ugh, older OpenSSL const-ness is a disaster.
#else
    auto* bio = ::BIO_new(&method);
#endif
    if (!bio) {
        return BioPtr();
    }
    set_bio_data(*bio, nullptr); // Just to make sure this isn't set yet.
    return BioPtr(bio);
}

} // anon ns

BioPtr new_mutable_direct_buffer_bio() {
    return new_direct_buffer_bio(*mutable_buf_method().method);
}

BioPtr new_const_direct_buffer_bio() {
    return new_direct_buffer_bio(*const_buf_method().method);
}

namespace {

int buffer_bio_init(::BIO* bio) {
    // "shutdown" here means "should BIO close underlying resource?". Since
    // our BIOs don't ever allocate anything we just use this value as something
    // that can be set by BIO_set_close() and read by BIO_get_close().
    set_bio_shutdown(*bio, 1);
    set_bio_init(*bio, 1);
    return 1;
}

int buffer_bio_destroy(::BIO* bio) {
    set_bio_data(*bio, nullptr); // We don't own anything.
    return 1;
}

int mutable_buffer_bio_write(::BIO* bio, const char* src_buf, int len) {
    LOG_ASSERT(len >= 0);

    BIO_clear_retry_flags(bio);
    if (!get_bio_data(*bio)) {
        // TODO replace with assertion once we _know_ it should never happen in practice..!
        LOG(error, "Got buffer write of length %d to a non-bound mutable BIO!", len);
        LOG(error, "%s", getStackTrace(0).c_str());
        return -1;
    }

    const auto sz_len = static_cast<size_t>(len);
    if (sz_len == 0) {
        return 0;
    }
    auto* dest_buf = static_cast<MutableBufferView*>(get_bio_data(*bio));
    // sz_len is <= INT32_MAX while pos/size are size_t, so no overflow on 64-bit
    // since the caller enforces that buffer sizes are < INT32_MAX.
    if (dest_buf->pos + sz_len > dest_buf->size) {
        return -1;
    }
    // Source and destination buffers should never overlap.
    memcpy(dest_buf->buffer + dest_buf->pos, src_buf, sz_len);
    dest_buf->pos += sz_len;

    return len;
}

int const_buffer_bio_write(::BIO* bio, const char* src_buf, int len) {
    (void) bio;
    (void) src_buf;
    // Const buffers are read only!
    LOG(error, "BIO_write() of length %d called on read-only BIO", len);
    return -1;
}

int mutable_buffer_bio_read(::BIO* bio, char* dest_buf, int len) {
    (void) bio;
    (void) dest_buf;
    // Mutable buffers are write only!
    LOG(error, "BIO_read() of length %d called on write-only BIO", len);
    return -1;
}

int const_buffer_bio_read(::BIO* bio, char* dest_buf, int len) {
    LOG_ASSERT(len >= 0);

    BIO_clear_retry_flags(bio);
    if (!get_bio_data(*bio)) {
        // TODO replace with assertion once we _know_ it should never happen in practice..!
        LOG(error, "Got buffer read of length %d to a non-bound const BIO!", len);
        LOG(error, "%s", getStackTrace(0).c_str());
        return -1;
    }

    const auto sz_len = static_cast<size_t>(len);
    auto* src_buf = static_cast<ConstBufferView*>(get_bio_data(*bio));
    const auto readable = std::min(sz_len, src_buf->size - src_buf->pos);
    if (readable != 0) {
        // Source and destination buffers should never overlap.
        memcpy(dest_buf, src_buf->buffer + src_buf->pos, readable);
        src_buf->pos += readable;
        return static_cast<int>(readable);
    }
    // Since a BIO might point to different buffers between SSL_* invocations,
    // we want OpenSSL to retry later. _Not_ setting this or not returning -1 will
    // cause OpenSSL to return SSL_ERROR_SYSCALL. Ask me how I know.
    BIO_set_retry_read(bio);
    return -1;
}

template <typename BufferType>
long do_buffer_bio_ctrl(::BIO* bio, int cmd, long num, void* ptr) {
    const auto* buf_view = static_cast<const BufferType*>(get_bio_data(*bio));
    long ret = 1;

    switch (cmd) {
    case BIO_CTRL_EOF: // Is the buffer exhausted?
        if (buf_view != nullptr) {
            ret = static_cast<int>(buf_view->pos == buf_view->size);
        }
        break;
    case BIO_CTRL_INFO: // How much data remains in buffer?
        ret = (buf_view != nullptr) ? buf_view->pending() : 0;
        if (ptr) {
            *static_cast<void**>(ptr) = nullptr; // Semantics: who knows? But everyone's doing it!
        }
        break;
    case BIO_CTRL_GET_CLOSE: // Is the BIO in auto close mode?
        ret = get_bio_shutdown(*bio);
        break;
    case BIO_CTRL_SET_CLOSE: // Should the BIO be in auto close mode? Spoiler alert: we don't really care.
        set_bio_shutdown(*bio, static_cast<int>(num));
        break;
    case BIO_CTRL_WPENDING:
        ret = 0;
        break;
    case BIO_CTRL_PENDING:
        ret = (buf_view != nullptr) ? buf_view->pending() : 0;
        break;
    case BIO_CTRL_DUP:
    case BIO_CTRL_FLUSH:
        ret = 1; // Same as memory OpenSSL BIO ctrl func.
        break;
    case BIO_CTRL_RESET:
    case BIO_C_SET_BUF_MEM:
    case BIO_C_GET_BUF_MEM_PTR:
    case BIO_C_SET_BUF_MEM_EOF_RETURN:
        LOG_ASSERT(!"Unsupported BIO control function called");
    case BIO_CTRL_PUSH:
    case BIO_CTRL_POP:
    default:
        ret = 0; // Not supported (but be gentle, since it's actually invoked)
        break;
    }
    return ret;
}

long mutable_buffer_bio_ctrl(::BIO* bio, int cmd, long num, void* ptr) {
    return do_buffer_bio_ctrl<MutableBufferView>(bio, cmd, num, ptr);
}

long const_buffer_bio_ctrl(::BIO* bio, int cmd, long num, void* ptr) {
    return do_buffer_bio_ctrl<ConstBufferView>(bio, cmd, num, ptr);
}

MutableBufferView mutable_buffer_view_of(char* buffer, size_t sz) {
    return {buffer, sz, 0, 0};
}

ConstBufferView const_buffer_view_of(const char* buffer, size_t sz) {
    return {buffer, sz, 0};
}

[[maybe_unused]] bool is_const_bio(::BIO& bio) noexcept {
    return (::BIO_method_type(&bio) == const_buf_method().type_index);
}

[[maybe_unused]] bool is_mutable_bio(::BIO& bio) noexcept {
    return (::BIO_method_type(&bio) == mutable_buf_method().type_index);
}

// There is a cute little bug in BIO_meth_new() present in v1.1.0h which
// causes the provided BIO method type to not be actually written into the
// target BIO_METHOD instance. This means that any assertions that check the
// BIO's method type on this version is doomed to fail.
// See https://github.com/openssl/openssl/pull/5812
#if ((OPENSSL_VERSION_NUMBER & 0xfffffff0L) != 0x10100080L)
#  define WHEN_NO_OPENSSL_BIO_TYPE_BUG(expr) expr
#else
#  define WHEN_NO_OPENSSL_BIO_TYPE_BUG(expr)
#endif

void set_bio_mutable_buffer_view(::BIO& bio, MutableBufferView* view) {
    WHEN_NO_OPENSSL_BIO_TYPE_BUG(LOG_ASSERT(is_mutable_bio(bio)));
    set_bio_data(bio, view);
}

void set_bio_const_buffer_view(::BIO& bio, ConstBufferView* view) {
    WHEN_NO_OPENSSL_BIO_TYPE_BUG(LOG_ASSERT(is_const_bio(bio)));
    set_bio_data(bio, view);
}

// Precondition: bio must have been created by a call to either
// new_mutable_direct_buffer_bio() or new_const_direct_buffer_bio()
void unset_bio_buffer_view(::BIO& bio) {
    WHEN_NO_OPENSSL_BIO_TYPE_BUG(LOG_ASSERT(is_mutable_bio(bio) || is_const_bio(bio)));
    set_bio_data(bio, nullptr);
}

} // anon ns

ConstBufferViewGuard::ConstBufferViewGuard(::BIO& bio, const char* buffer, size_t sz) noexcept
    : _bio(bio),
      _view(const_buffer_view_of(buffer, sz))
{
    WHEN_NO_OPENSSL_BIO_TYPE_BUG(LOG_ASSERT(is_const_bio(bio)));
    set_bio_const_buffer_view(bio, &_view);
}

ConstBufferViewGuard::~ConstBufferViewGuard() {
    unset_bio_buffer_view(_bio);
}

MutableBufferViewGuard::MutableBufferViewGuard(::BIO& bio, char* buffer, size_t sz) noexcept
    : _bio(bio),
      _view(mutable_buffer_view_of(buffer, sz))
{
    WHEN_NO_OPENSSL_BIO_TYPE_BUG(LOG_ASSERT(is_mutable_bio(bio)));
    set_bio_mutable_buffer_view(bio, &_view);
}

MutableBufferViewGuard::~MutableBufferViewGuard() {
    unset_bio_buffer_view(_bio);
}

}
