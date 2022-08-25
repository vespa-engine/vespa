// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "identity.h"
#include <vespa/slobrok/cfg.h>
#include <vespa/vespalib/net/tls/capability_set.h>
#include <vespa/vespalib/util/compressionconfig.h>

namespace mbus {

/**
 * To facilitate several configuration parameters to the {@link RPCNetwork} constructor, all parameters are
 * held by this class. This class has reasonable default values for each parameter.
 */
class RPCNetworkParams {
private:
    using CompressionConfig = vespalib::compression::CompressionConfig;
    using CapabilitySet     = vespalib::net::tls::CapabilitySet;
    Identity          _identity;
    config::ConfigUri _slobrokConfig;
    int               _listenPort;
    uint32_t          _maxInputBufferSize;
    uint32_t          _maxOutputBufferSize;
    uint32_t          _numNetworkThreads;
    uint32_t          _numRpcTargets;
    uint32_t          _events_before_wakeup;
    bool              _tcpNoDelay;
    double            _connectionExpireSecs;
    CompressionConfig _compressionConfig;
    CapabilitySet     _required_capabilities;

public:
    RPCNetworkParams();
    RPCNetworkParams(config::ConfigUri configUri);
    ~RPCNetworkParams();

    /**
     * Sets number of threads for the network.
     *
     * @param numNetworkThreads number of threads for the network
     * @return This, to allow chaining.
     */
     RPCNetworkParams &setNumNetworkThreads(uint32_t numNetworkThreads) {
         _numNetworkThreads = numNetworkThreads;
         return *this;
     }

    uint32_t getNumNetworkThreads() const { return _numNetworkThreads; }

    RPCNetworkParams &setNumRpcTargets(uint32_t numRpcTargets) {
        _numRpcTargets = numRpcTargets;
        return *this;
    }

    uint32_t getNumRpcTargets() const { return _numRpcTargets; }

    /**
     * Returns the identity to use for the network.
     *
     * @return The identity.
     */
    const Identity &getIdentity() const {
        return _identity;
    }

    /**
     * Sets the identity to use for the network.
     *
     * @param identity The new identity.
     * @return This, to allow chaining.
     */
    RPCNetworkParams &setIdentity(const Identity &identity) {
        _identity = identity;
        return *this;
    }

    /**
     * Sets the identity to use for the network.
     *
     * @param identity A string representation of the identity.
     * @return This, to allow chaining.
     */
    RPCNetworkParams &setIdentity(const string &identity) {
        return setIdentity(Identity(identity));
    }

    /**
     * Returns the config id of the slobrok config.
     *
     * @return The config id.
     */
    const config::ConfigUri & getSlobrokConfig() const {
        return _slobrokConfig;
    }

    /**
     * Returns the port to listen to.
     *
     * @return The port.
     */
    int getListenPort() const {
        return _listenPort;
    }

    /**
     * Sets the port to listen to.
     *
     * @param listenPort The new port.
     * @return This, to allow chaining.
     */
    RPCNetworkParams &setListenPort(int listenPort) {
        _listenPort = listenPort;
        return *this;
    }

    RPCNetworkParams &setTcpNoDelay(bool tcpNoDelay) {
        _tcpNoDelay = tcpNoDelay;
        return *this;
    }

    bool getTcpNoDelay() const { return _tcpNoDelay; }

    /**
     * Returns the number of seconds before an idle network connection expires.
     *
     * @return The number of seconds.
     */
    double getConnectionExpireSecs() const{
        return _connectionExpireSecs;
    }

    /**
     * Sets the number of seconds before an idle network connection expires.
     *
     * @param secs The number of seconds.
     * @return This, to allow chaining.
     */
    RPCNetworkParams &setConnectionExpireSecs(double secs) {
        _connectionExpireSecs = secs;
        return *this;
    }

    /**
     * Returns the maximum input buffer size allowed for the underlying FNET connection.
     *
     * @return The maximum number of bytes.
     */
    uint32_t getMaxInputBufferSize() const {
        return _maxInputBufferSize;
    }

    /**
     * Returns the maximum output buffer size allowed for the underlying FNET connection.
     *
     * @return The maximum number of bytes.
     */
    uint32_t getMaxOutputBufferSize() const {
        return _maxOutputBufferSize;
    }

    RPCNetworkParams &setCompressionConfig(CompressionConfig compressionConfig) {
        _compressionConfig = compressionConfig;
        return *this;
    }
    CompressionConfig getCompressionConfig() const { return _compressionConfig; }

    RPCNetworkParams &events_before_wakeup(uint32_t value) {
        _events_before_wakeup = value;
        return *this;
    }
    uint32_t events_before_wakeup() const { return _events_before_wakeup; }

    RPCNetworkParams& required_capabilities(CapabilitySet capabilities) noexcept {
        _required_capabilities = capabilities;
        return *this;
    }
    [[nodiscard]] CapabilitySet required_capabilities() const noexcept {
        return _required_capabilities;
    }
};

}

