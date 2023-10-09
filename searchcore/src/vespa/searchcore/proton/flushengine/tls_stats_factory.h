// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "i_tls_stats_factory.h"
#include <memory>

namespace search { namespace transactionlog { class TransLogServer; } }

namespace proton::flushengine {

/*
 * Class used to create statistics for a transaction log server over
 * multiple domains.
 */
class TlsStatsFactory : public ITlsStatsFactory
{
    using TransLogServer = search::transactionlog::TransLogServer;
    std::shared_ptr<TransLogServer> _tls;
public:
    TlsStatsFactory(std::shared_ptr<TransLogServer> tls);
    virtual ~TlsStatsFactory();
    virtual TlsStatsMap create() override;
};

}
