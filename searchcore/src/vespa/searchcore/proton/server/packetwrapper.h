// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tls_replay_progress.h"
#include <vespa/searchlib/transactionlog/common.h>
#include <vespa/searchlib/transactionlog/client_common.h>
#include <vespa/vespalib/util/gate.h>

namespace proton {
/**
 * Wrapper of transaction log packet to use when handing over to
 * executor thread.
 */
struct PacketWrapper {
    using SP = std::shared_ptr<PacketWrapper>;

    const search::transactionlog::Packet &packet;
    TlsReplayProgress *progress;
    search::transactionlog::client::RPC::Result result;
    vespalib::Gate gate;

    PacketWrapper(const search::transactionlog::Packet &p, TlsReplayProgress *progress_) noexcept
        : packet(p),
          progress(progress_),
          result(search::transactionlog::client::RPC::ERROR),
          gate()
    {
    }
};

}  // namespace proton

