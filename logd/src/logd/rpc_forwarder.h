// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "forwarder.h"
#include "proto_converter.h"
#include <vespa/log/log_message.h>
#include <vespa/fnet/frt/target.h>
#include <memory>
#include <vector>

class FRT_Supervisor;

namespace logdemon {

struct Metrics;

struct RpcTargetSubRef {
    void operator()(FRT_Target* target) const noexcept {
        target->SubRef();
    }
};
using RpcTargetGuard = std::unique_ptr<FRT_Target, RpcTargetSubRef>;

/**
 * Implementation of the Forwarder interface that uses RPC to send protobuf encoded log messages to the logserver.
 */
class RpcForwarder : public Forwarder {
private:
    Metrics& _metrics;
    vespalib::string _connection_spec;
    double _rpc_timeout_secs;
    size_t _max_messages_per_request;
    RpcTargetGuard _target;
    std::vector<ns_log::LogMessage> _messages;
    int _bad_lines;
    ForwardMap _forward_filter;

    void ping_logserver();

public:
    RpcForwarder(Metrics& metrics, const ForwardMap& forward_filter, FRT_Supervisor& supervisor,
                 const vespalib::string& logserver_host, int logserver_rpc_port,
                 double rpc_timeout_secs, size_t max_messages_per_request);
    ~RpcForwarder() override;

    // Implements Forwarder
    void forwardLine(std::string_view line) override;
    void flush() override;
    int badLines() const override;
    void resetBadLines() override;
};

}

