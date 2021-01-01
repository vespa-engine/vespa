// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>
#include <shared_mutex>

namespace document { class DocumentTypeRepo; }
namespace storage::mbusprot { class ProtocolSerialization7; }

namespace storage::rpc {

class WrappedCodec {
    const std::shared_ptr<const document::DocumentTypeRepo> _doc_type_repo;
    std::unique_ptr<mbusprot::ProtocolSerialization7> _codec;
public:
    explicit WrappedCodec(std::shared_ptr<const document::DocumentTypeRepo> doc_type_repo);
    ~WrappedCodec();

    [[nodiscard]] const mbusprot::ProtocolSerialization7& codec() const noexcept { return *_codec; }
};

/**
 * Thread-safe wrapper around a protocol serialization codec and its transitive
 * dependencies. Effectively provides support for setting and getting an immutable
 * codec snapshot that can be used for RPC (de-)serialization.
 */
class MessageCodecProvider {
    // TODO replace with std::atomic<std::shared_ptr<WrappedCodec>> once on a sufficiently new
    // C++20 STL that implements the P0718R2 proposal. We expect(tm) an implementation to use
    // lock-free compiler-specific 128-bit CAS atomics instead of explicit locks there.
    mutable std::shared_mutex _rw_mutex;
    std::shared_ptr<WrappedCodec> _active_codec;
public:
    explicit MessageCodecProvider(std::shared_ptr<const document::DocumentTypeRepo> doc_type_repo);
    ~MessageCodecProvider();

    [[nodiscard]] std::shared_ptr<const WrappedCodec> wrapped_codec() const noexcept;

    void update_atomically(std::shared_ptr<const document::DocumentTypeRepo> doc_type_repo);
};

}
