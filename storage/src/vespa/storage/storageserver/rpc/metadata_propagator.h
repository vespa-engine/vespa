// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace storage::api {
class MetadataExtractor;
class MetadataInjector;
class StorageCommand;
}

namespace storage::rpc {

/**
 * Used to propagate StorageCommand-level metadata to and from RPC-level metadata.
 *
 * All methods must be fully thread safe.
 */
class MetadataPropagator {
public:
    virtual ~MetadataPropagator() = default;

    /**
     * Called at the time of serializing a StorageCommand to the underlying wire protocol.
     * Allows for injecting any metadata key/value pairs the StorageCommand wants to propagate
     * to the receiver, but that aren't part of the per-message schema itself.
     *
     * A propagator (if present) on the receiver side will have on_command_received()
     * invoked with the newly materialized StorageCommand instance alongside an extractor
     * that can read the values set by the sender.
     *
     * The transport carrier shall guarantee that the metadata injected will not be
     * compressed during transport.
     *
     * The lifetime of the injector is only valid for the duration of the call.
     */
    virtual void on_send_command(const api::StorageCommand& cmd, api::MetadataInjector& injector) const = 0;

    /**
     * Invoked when a StorageCommand arrives at a storage server, with an extractor that
     * can resolve key/value metadata sent for that command.
     *
     * This method is always invoked by the RPC layer right after it has been decoded
     * but _before_ it is passed to any message handlers.
     *
     * The lifetime of the extractor is only valid for the duration of the call.
     */
    virtual void on_receive_command(api::StorageCommand& cmd, const api::MetadataExtractor& extractor) const = 0;
};

}
