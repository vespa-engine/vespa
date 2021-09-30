// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tls_replay_progress.h"
#include "transactionlogmanagerbase.h"

namespace proton {
struct ConfigStore;

/**
 * Class managing the initialization and replay of the transaction log.
 **/
class TransactionLogManager : public TransactionLogManagerBase
{
    std::unique_ptr<Visitor> _visitor;

    void doLogReplayComplete(const vespalib::string &domainName, vespalib::duration elapsedTime) const override;

public:
    /**
     * Create a new manager.
     *
     * @param tlsSpec the spec of the transaction log server.
     * @param domainName the name of the domain this manager should handle.
     **/
    TransactionLogManager(const vespalib::string &tlsSpec, const vespalib::string &domainName);
    ~TransactionLogManager() override;

    /**
     * Init the transaction log.
     *
     * @param oldestConfigSerial the serial num of the oldest config.
     * @param the pruned serial num will be set to 1 lower than
     *        the serial num of the first entry in the transaction log.
     * @param replay_end_serial_num will be set to the serial num of
     *        the last entry in the transaction log.
     **/
    void init(SerialNum oldestConfigSerial, SerialNum &prunedSerialNum, SerialNum &replay_end_serial_num);

    /**
     * Prepare replay of the transaction log.
     **/
    static void
    prepareReplay(TransLogClient &client,
                  const vespalib::string &domainName,
                  SerialNum flushedIndexMgrSerial,
                  SerialNum flushedSummaryMgrSerial,
                  ConfigStore &config_store);


    /*
     * Make a tls replay progress object for serial numbers (first..last]
     */
    std::unique_ptr<TlsReplayProgress>
    make_replay_progress(SerialNum first, SerialNum last);

    /**
     * Start replay of the transaction log.
     **/
    void startReplay(SerialNum first, SerialNum syncToken, Callback &callback);

    /**
     * Indicate that replay is done.
     * Should be called when session callback handles eof().
     **/
    void replayDone();
};

} // namespace proton

