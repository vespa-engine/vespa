// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/searchcore/proton/feedoperation/feedoperation.h>
#include <vespa/searchlib/query/base.h>
#include <vespa/searchlib/common/serialnum.h>
#include <vespa/searchlib/transactionlog/translogclient.h>
#include "fileconfigmanager.h"
#include <persistence/spi/types.h>

namespace proton {

class TlcProxy {
    search::transactionlog::TransLogClient::Session & _session;
    search::transactionlog::Writer                  * _tlsDirectWriter;

    void commit( search::SerialNum serialNum, search::transactionlog::Type type, const vespalib::nbostream &buf);
public:
    typedef std::unique_ptr<TlcProxy> UP;

    TlcProxy(search::transactionlog::TransLogClient::Session &session, search::transactionlog::Writer * writer = NULL)
        : _session(session), _tlsDirectWriter(writer) {}

    void storeOperation(const FeedOperation &op);
};

} // namespace proton

