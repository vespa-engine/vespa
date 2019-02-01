// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>

namespace vespalib::net::tls {

struct HandshakeResult {
    // Handshake bytes consumed from peer.
    size_t bytes_consumed = 0;
    // Handshake bytes produced that must be sent to the peer.
    size_t bytes_produced = 0;
    enum class State {
        Failed,
        Done,
        NeedsMorePeerData
    };
    State state = State::Failed;

    bool failed() const noexcept { return (state == State::Failed); }
    bool done() const noexcept { return (state == State::Done); }
};

struct EncodeResult {
    // Plaintext bytes consumed
    size_t bytes_consumed = 0;
    // Ciphertext bytes produced that must be sent to the peer
    size_t bytes_produced = 0;
    bool failed = true;
};

struct DecodeResult {
    // Ciphertext bytes consumed from peer
    size_t bytes_consumed = 0;
    // Plaintext bytes produced.
    size_t bytes_produced = 0;
    enum class State {
        Failed,
        OK,
        NeedsMorePeerData,
        Closed
    };
    State state = State::Failed;

    bool closed() const noexcept { return (state == State::Closed); }
    bool failed() const noexcept { return (state == State::Failed); }
    bool frame_decoded_ok() const noexcept { return (state == State::OK); }
};

struct TlsContext;

// TODO move to different namespace, not dependent on TLS?

/*
 * A CryptoCodec provides a fully transport-independent way of negotiating
 * a secure, authenticated session towards another peer. The codec requires
 * the caller to handle any and all actual data transfer
 */
class CryptoCodec {
public:
    enum class Mode {
        Client, Server
    };

    virtual ~CryptoCodec() = default;

    /*
     * Minimum buffer size required to represent one wire format frame
     * of encrypted (ciphertext) data, including frame overhead.
     */
    virtual size_t min_encode_buffer_size() const noexcept = 0;
    /*
     * Minimum buffer size required to represent the decoded (plaintext)
     * output of a single frame of encrypted data.
     */
    virtual size_t min_decode_buffer_size() const noexcept = 0;

    /*
     * Precondition:  to_peer_buf_size >= min_encode_buffer_size()
     * Postcondition: if result.done(), the handshake process has completed
     *                and data may be passed through encode()/decode().
     */
    virtual HandshakeResult handshake(const char* from_peer, size_t from_peer_buf_size,
                                      char* to_peer, size_t to_peer_buf_size) noexcept = 0;

    /*
     * Encodes a single ciphertext frame into `ciphertext`. If plaintext_size
     * is greater than can fit into a frame, the returned result's consumed_bytes
     * field will be < plaintext_size. The number of actual ciphertext bytes produced
     * is available in the returned result's produced_bytes field.
     *
     * Precondition:  handshake must be completed
     * Precondition:  ciphertext_size >= min_encode_buffer_size(), i.e. it must be
     *                possible to encode at least 1 frame.
     * Postcondition: if plaintext_size > 0 and result.failed == false, a single
     *                frame of ciphertext has been written into the to_peer buffer.
     *                Size of written frame is given by result.bytes_produced. This
     *                includes all protocol-specific frame overhead.
     */
    virtual EncodeResult encode(const char* plaintext, size_t plaintext_size,
                                char* ciphertext, size_t ciphertext_size) noexcept = 0;
    /*
     * Attempt to decode ciphertext sent by the peer into plaintext. Since
     * ciphertext is sent in frames, it's possible that invoking decode()
     * may produce a CodecResult with a state of `NeedsMorePeerData` if a
     * complete frame is not present in `ciphertext`. In this case, decode()
     * must be called again once more data is available.
     *
     * If result.closed() == true, the peer has half-closed their connection
     * and no more data may be decoded.
     *
     * Precondition:  handshake must be completed
     * Precondition:  plaintext_size >= min_decode_buffer_size()
     * Postcondition: if result.state == DecodeResult::State::OK, at least 1
     *                complete frame has been written to the `plaintext` buffer
     */
    virtual DecodeResult decode(const char* ciphertext, size_t ciphertext_size,
                                char* plaintext, size_t plaintext_size) noexcept = 0;

    /**
     * Encodes a frame into `ciphertext` which signals to the peer that all writes
     * are complete. The peer may still send data to be decoded.
     *
     * After calling this method, encode() must not be called on the same codec instance.
     *
     * Precondition: ciphertext_size >= min_encode_buffer_size(), i.e. it must be
     *               possible to encode at least 1 frame.
     */
    virtual EncodeResult half_close(char* ciphertext, size_t ciphertext_size) noexcept = 0;

    /*
     * Creates an implementation defined CryptoCodec that provides at least TLSv1.2
     * compliant handshaking and full duplex data transfer.
     *
     * Throws CryptoException if resources cannot be allocated for the codec.
     */
    static std::unique_ptr<CryptoCodec> create_default_codec(std::shared_ptr<TlsContext> ctx, Mode mode);
};

}
