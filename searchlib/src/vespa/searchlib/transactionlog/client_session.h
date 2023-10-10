// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "client_common.h"
#include <vespa/searchlib/common/serialnum.h>
#include <vespa/vespalib/util/buffer.h>
#include <vespa/vespalib/stllike/string.h>

class FRT_RPCRequest;

namespace search::transactionlog::client {

class TransLogClient;

class SessionKey
{
public:
    SessionKey(const vespalib::string & domain, int sessionId);
    ~SessionKey();
    bool operator < (const SessionKey & b) const { return cmp(b) < 0; }
private:
    int cmp(const SessionKey & b) const;
    vespalib::string _domain;
    int         _sessionId;
};

class Session
{
public:
    Session(const vespalib::string & domain, TransLogClient & tlc);
    virtual ~Session();
    /// You can commit data of any registered type to any channel.
    bool commit(const vespalib::ConstBufferRef & packet);
    /// Will erase all entries prior to <to>
    bool erase(const SerialNum & to);
    bool status(SerialNum & b, SerialNum & e, size_t & count);

    bool sync(const SerialNum &syncTo, SerialNum &syncedTo);

    virtual RPC::Result visit(const Packet & ) { return RPC::OK; }
    virtual void eof()    { }
    bool close();
    void clear();
    const vespalib::string & getDomain() const { return _domain; }
    const TransLogClient & getTLC() const { return _tlc; }
protected:
    bool init(FRT_RPCRequest * req);
    bool run();
    TransLogClient & _tlc;
    vespalib::string _domain;
    int              _sessionId;
};

/// Here you connect to the incomming data getting everything from <from>
class Visitor : public Session
{
public:
    Visitor(const vespalib::string & domain, TransLogClient & tlc, Callback & callBack);
    bool visit(const SerialNum & from, const SerialNum & to);
    virtual ~Visitor() override;
    RPC::Result visit(const Packet & packet) override { return _callback.receive(packet); }
    void eof() override    { _callback.eof(); }
private:
    Callback & _callback;
};

}

