// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "crypto_codec_adapter.h"
#include <vespa/vespalib/net/connection_auth_context.h>
#include <assert.h>

namespace vespalib::net::tls {

CryptoSocket::HandshakeResult
CryptoCodecAdapter::hs_try_flush()
{
    auto flush_res = flush_all();
    if (flush_res == 0) {
        return HandshakeResult::DONE;
    } else if (is_blocked(flush_res, errno)) {
        return HandshakeResult::NEED_WRITE;
    } else {
        return HandshakeResult::FAIL;
    }
}

CryptoSocket::HandshakeResult
CryptoCodecAdapter::hs_try_fill()
{
    auto fill_res = fill_input();
    if (fill_res > 0) {
        return HandshakeResult::DONE;
    } else if (is_blocked(fill_res, errno)) {
        return HandshakeResult::NEED_READ;
    } else { // eof included here
        return HandshakeResult::FAIL;
    }
}

ssize_t
CryptoCodecAdapter::fill_input()
{
    if (_input.obtain().size < _codec->min_encode_buffer_size()) {
        auto dst = _input.reserve(_codec->min_encode_buffer_size());
        ssize_t res = _socket.read(dst.data, dst.size);
        if (res > 0) {
            _input.commit(res);
        } else {
            return res; // eof/error
        }
    }
    return 1; // progress
}

ssize_t
CryptoCodecAdapter::flush_all()
{
    ssize_t res = flush();
    while (res > 0) {
        res = flush();
    }
    return res;
}

void
CryptoCodecAdapter::inject_read_data(const char *buf, size_t len)
{
    if (len > 0) {
        auto dst = _input.reserve(len);
        memcpy(dst.data, buf, len);
        _input.commit(len);
    }
}

CryptoSocket::HandshakeResult
CryptoCodecAdapter::handshake() 
{
    for (;;) {
        auto in = _input.obtain();
        auto out = _output.reserve(_codec->min_encode_buffer_size());
        auto hs_res = _codec->handshake(in.data, in.size, out.data, out.size);
        _input.evict(hs_res.bytes_consumed);
        _output.commit(hs_res.bytes_produced);
        switch (hs_res.state) {
        case ::vespalib::net::tls::HandshakeResult::State::Failed: return HandshakeResult::FAIL;
        case ::vespalib::net::tls::HandshakeResult::State::Done: return hs_try_flush();
        case ::vespalib::net::tls::HandshakeResult::State::NeedsWork: return HandshakeResult::NEED_WORK;
        case ::vespalib::net::tls::HandshakeResult::State::NeedsMorePeerData:
            auto flush_res = hs_try_flush();
            if (flush_res != HandshakeResult::DONE) {
                return flush_res;
            }
            auto fill_res = hs_try_fill();
            if (fill_res != HandshakeResult::DONE) {
                return fill_res;
            }
        }
    }
    return HandshakeResult::DONE;
}

void
CryptoCodecAdapter::do_handshake_work()
{
    _codec->do_handshake_work();
}

ssize_t
CryptoCodecAdapter::read(char *buf, size_t len)
{
    auto drain_res = drain(buf, len);
    if ((drain_res != 0) || _got_tls_close) {
        return drain_res;
    }
    auto fill_res = fill_input();
    if (fill_res <= 0) {
        if (fill_res == 0) {
            fill_res = -1;
            errno = EIO;
        }
        return fill_res;
    }
    drain_res = drain(buf, len);
    if ((drain_res != 0) || _got_tls_close) {
        return drain_res;
    }
    errno = EWOULDBLOCK;
    return -1;
}

ssize_t
CryptoCodecAdapter::drain(char *buf, size_t len)
{
    auto src = _input.obtain();
    auto res = _codec->decode(src.data, src.size, buf, len);
    if (res.failed()) {
        errno = EIO;
        return -1;        
    }
    if (res.closed()) {
        _got_tls_close = true;
    }
    _input.evict(res.bytes_consumed);
    return res.bytes_produced;
}

ssize_t
CryptoCodecAdapter::write(const char *buf, size_t len)
{
    if (_output.obtain().size >= _codec->min_encode_buffer_size()) {
        if (flush() < 0) {
            return -1;
        }
        if (_output.obtain().size > 0) {
            errno = EWOULDBLOCK;
            return -1;
        }
    }
    auto dst = _output.reserve(_codec->min_encode_buffer_size());
    auto res = _codec->encode(buf, len, dst.data, dst.size);
    if (res.failed) {
        errno = EIO;
        return -1;
    }
    _output.commit(res.bytes_produced);
    return res.bytes_consumed;
}

ssize_t
CryptoCodecAdapter::flush()
{
    auto pending = _output.obtain();
    if (pending.size > 0) {
        ssize_t res = _socket.write(pending.data, pending.size);
        if (res > 0) {
            _output.evict(res);
            return 1; // progress
        } else {
            assert(res < 0);
            return -1; // error
        }
    }
    return 0; // done
}

ssize_t
CryptoCodecAdapter::half_close()
{
    auto flush_res = flush_all();
    if (flush_res < 0) {
        return flush_res;
    }
    if (!_encoded_tls_close) {
        auto dst = _output.reserve(_codec->min_encode_buffer_size());
        auto res = _codec->half_close(dst.data, dst.size);
        if (res.failed) {
            errno = EIO;
            return -1;
        }
        _output.commit(res.bytes_produced);
        _encoded_tls_close = true;
    }
    flush_res = flush_all();
    if (flush_res < 0) {
        return flush_res;
    }
    return _socket.half_close();
}

void
CryptoCodecAdapter::drop_empty_buffers()
{
    _input.drop_if_empty();
    _output.drop_if_empty();
}

std::unique_ptr<net::ConnectionAuthContext>
CryptoCodecAdapter::make_auth_context()
{
    return std::make_unique<net::ConnectionAuthContext>(_codec->peer_credentials(), _codec->granted_capabilities());
}

} // namespace vespalib::net::tls
