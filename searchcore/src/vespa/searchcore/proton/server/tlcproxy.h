// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/transactionlog/common.h>

namespace proton {

class FeedOperation;

class TlcProxy {
    vespalib::string                 _domain;
    search::transactionlog::Writer & _tlsDirectWriter;

    void commit(search::SerialNum serialNum, search::transactionlog::Type type, const vespalib::nbostream &buf);
public:
    typedef std::unique_ptr<TlcProxy> UP;

    TlcProxy(const vespalib::string & domain, search::transactionlog::Writer & writer)
        : _domain(domain), _tlsDirectWriter(writer) {}

    void storeOperation(const FeedOperation &op);
};

} // namespace proton

