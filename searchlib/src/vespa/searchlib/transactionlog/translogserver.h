// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/transactionlog/domain.h>
#include <vespa/vespalib/util/document_runnable.h>
#include <vespa/document/util/queue.h>
#include <mutex>

namespace search
{

namespace common
{

class FileHeaderContext;

}

namespace transactionlog
{

class TransLogServerExplorer;

class TransLogServer : public document::Runnable, private FRT_Invokable, public Writer
{
public:
    friend class TransLogServerExplorer;
    typedef std::unique_ptr<TransLogServer> UP;
    typedef std::shared_ptr<TransLogServer> SP;

    TransLogServer(const vespalib::string &name,
                   int listenPort,
                   const vespalib::string &baseDir,
                   const common::FileHeaderContext &fileHeaderContext,
                   uint64_t domainPartSize=0x10000000,
                   bool useFsync=false,
                   size_t maxThreads=4,
                   DomainPart::Crc defaultCrc=DomainPart::xxh64);
    virtual ~TransLogServer();
    uint64_t getDomainPartSize() const { return _domainPartSize; }
    uint64_t setDomainPartSize();
    DomainStats getDomainStats() const;

    void commit(const vespalib::string & domainName, const Packet & packet) override;


    class Session
    {
        bool _down;
    public:
        typedef std::shared_ptr<Session> SP;

        Session() : _down(false) { }
        bool getDown() const { return _down; }
        void setDown() { _down = true; }
    };

private:
    bool onStop() override;
    void run() override;
    void exportRPC(FRT_Supervisor & supervisor);
    void relayToThreadRPC(FRT_RPCRequest *req);

    void createDomain(FRT_RPCRequest *req);
    void deleteDomain(FRT_RPCRequest *req);
    void openDomain(FRT_RPCRequest *req);
    void listDomains(FRT_RPCRequest *req);

    void domainStatus(FRT_RPCRequest *req);
    void domainCommit(FRT_RPCRequest *req);
    void domainSessionRun(FRT_RPCRequest *req);
    void domainPrune(FRT_RPCRequest *req);
    void domainVisit(FRT_RPCRequest *req);
    void domainSubscribe(FRT_RPCRequest *req);
    void domainSessionClose(FRT_RPCRequest *req);
    void domainSync(FRT_RPCRequest *req);

    void initSession(FRT_RPCRequest *req);
    void finiSession(FRT_RPCRequest *req);
    void downSession(FRT_RPCRequest *req);

    void logMetric() const;
    std::vector<vespalib::string> getDomainNames();
    Domain::SP findDomain(const vespalib::stringref &name);
    vespalib::string dir()        const { return _baseDir + "/" + _name; }
    vespalib::string domainList() const { return dir() + "/" + _name + ".domains"; }

    static const Session::SP & getSession(FRT_RPCRequest *req);

    typedef std::map<vespalib::string, Domain::SP > DomainList;

    vespalib::string                   _name;
    vespalib::string                   _baseDir;
    const uint64_t                     _domainPartSize;
    const bool                         _useFsync;
    const DomainPart::Crc              _defaultCrcType;
    vespalib::ThreadStackExecutor      _executor;
    FastOS_ThreadPool                  _threadPool;
    FRT_Supervisor                     _supervisor;
    DomainList                         _domains;
    mutable std::mutex                 _lock;          // Protects _domains
    std::mutex                         _fileLock;      // Protects the creating and deleting domains including file system operations.
    document::Queue<FRT_RPCRequest *>  _reqQ;
    const common::FileHeaderContext    &_fileHeaderContext;
    using Guard = std::lock_guard<std::mutex>;
};

}
}

