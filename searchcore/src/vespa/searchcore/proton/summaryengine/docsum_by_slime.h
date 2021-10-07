// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/engine/docsumapi.h>
#include <vespa/vespalib/data/slime/slime.h>

class FRT_RPCRequest;

namespace proton {

class DocsumBySlime {
    using DocsumServer = search::engine::DocsumServer;
    using DocsumRequest = search::engine::DocsumRequest;
    using DocsumReply = search::engine::DocsumReply;
    using Inspector = vespalib::slime::Inspector;
public:
    typedef std::unique_ptr<DocsumBySlime> UP;
    DocsumBySlime(DocsumServer & docsumServer) : _docsumServer(docsumServer) { }
    vespalib::Slime::UP getDocsums(const Inspector & req);
    static DocsumRequest::UP slimeToRequest(const Inspector & req);
private:
    DocsumServer & _docsumServer;
};

class DocsumByRPC
{
public:
    DocsumByRPC(DocsumBySlime & slimeDocsumServer);
    void getDocsums(FRT_RPCRequest & req);
private:
    DocsumBySlime & _slimeDocsumServer;
};

}
