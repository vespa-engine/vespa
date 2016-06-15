// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tls_replay_progress.h"
#include <vespa/searchlib/transactionlog/common.h>
#include <vespa/vespalib/util/sync.h>

namespace proton {
/**
 * Wrapper of transaction log packet to use when handing over to
 * executor thread.
 */
struct PacketWrapper {
    typedef std::shared_ptr<PacketWrapper> SP;

    const search::transactionlog::Packet &packet;
    TlsReplayProgress *progress;
    search::transactionlog::RPC::Result result;
    vespalib::Gate gate;

    PacketWrapper(const search::transactionlog::Packet &p,
                  TlsReplayProgress *progress_)
        : packet(p),
          progress(progress_),
          result(search::transactionlog::RPC::ERROR),
          gate()
    {
    }
};

}  // namespace proton

