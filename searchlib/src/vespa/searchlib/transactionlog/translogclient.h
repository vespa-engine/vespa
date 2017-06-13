// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "common.h"
#include <vespa/document/util/bytebuffer.h>
#include <vespa/vespalib/util/sync.h>
#include <vespa/vespalib/util/buffer.h>
#include <vespa/fnet/frt/invokable.h>
#include <map>
#include <vector>

class FRT_Supervisor;
class FRT_Target;

namespace search::transactionlog {

class TransLogClient : private FRT_Invokable
{
private:
    TransLogClient(const TransLogClient &);
    TransLogClient& operator=(const TransLogClient &);

public:
    class Session
    {
    public:
        class Callback {
        public:
            virtual ~Callback() { }
            virtual RPC::Result receive(const Packet & packet) = 0;
            virtual void inSync() { }
            virtual void eof() { }
        };
    public:
        typedef std::unique_ptr<Session> UP;
        typedef std::shared_ptr<Session> SP;

        Session(const vespalib::string & domain, TransLogClient & tlc);
        virtual ~Session();
        /// You can commit data of any registered type to any channel.
        bool commit(const vespalib::ConstBufferRef & packet);
        /// Will erase all entries prior to <to>
        bool erase(const SerialNum & to);
        bool status(SerialNum & b, SerialNum & e, size_t & count);

        bool sync(const SerialNum &syncTo, SerialNum &syncedTo);

        virtual RPC::Result visit(const Packet & ) { return RPC::OK; }
        virtual void inSync() { }
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
    class Subscriber : public Session
    {
    public:
        typedef std::unique_ptr<Subscriber> UP;
        typedef std::shared_ptr<Subscriber> SP;

        Subscriber(const vespalib::string & domain, TransLogClient & tlc, Callback & callBack);
        bool subscribe(const SerialNum & from);
        ~Subscriber();
        RPC::Result visit(const Packet & packet) override { return _callback.receive(packet); }
        void inSync() override { _callback.inSync(); }
        void eof() override    { _callback.eof(); }
    private:
        Callback & _callback;
    };
    /// Here you read the incomming data getting everything from <from>
    class Visitor : public Subscriber
    {
    public:
        typedef std::unique_ptr<Visitor> UP;
        typedef std::shared_ptr<Visitor> SP;

        Visitor(const vespalib::string & domain, TransLogClient & tlc, Callback & callBack);
        bool visit(const SerialNum & from, const SerialNum & to);
        virtual ~Visitor();
    };
public:
    typedef std::unique_ptr<TransLogClient> UP;

    TransLogClient(const vespalib::string & rpctarget);
    virtual ~TransLogClient();

    /// Here you create a new domain
    bool create(const vespalib::string & domain);
    /// Here you remove a domain
    bool remove(const vespalib::string & domain);
    /// Here you open an existing domain
    Session::UP open(const vespalib::string & domain);
    /// Here you can get a list of available domains.
    bool listDomains(std::vector<vespalib::string> & dir);
    /// Here you get a subscriber
    Subscriber::UP createSubscriber(const vespalib::string & domain, Session::Callback & callBack);
    Visitor::UP createVisitor(const vespalib::string & domain, Session::Callback & callBack);

    bool isConnected() const;
    void disconnect();
    bool reconnect();
    const vespalib::string &getRPCTarget() const { return _rpcTarget; }
private:
    void exportRPC(FRT_Supervisor & supervisor);
    void visitCallbackRPC(FRT_RPCRequest *req);
    void syncCallbackRPC(FRT_RPCRequest *req);
    void eofCallbackRPC(FRT_RPCRequest *req);
    int32_t rpc(FRT_RPCRequest * req);
    Session * findSession(const vespalib::string & domain, int sessionId);

    class SessionKey
    {
    public:
        SessionKey(const vespalib::string & domain, int sessionId) : _domain(domain), _sessionId(sessionId) { }
        bool operator < (const SessionKey & b) const { return cmp(b) < 0; }
    private:
        int cmp(const SessionKey & b) const;
        vespalib::string _domain;
        int         _sessionId;
    };

    typedef std::map< SessionKey, Session * > SessionMap;

    vespalib::string _rpcTarget;
    SessionMap       _sessions;
    //Brute force lock for subscriptions. For multithread safety.
    vespalib::Lock   _lock;
    std::unique_ptr<FRT_Supervisor>   _supervisor;
    FRT_Target     * _target;
};

}

