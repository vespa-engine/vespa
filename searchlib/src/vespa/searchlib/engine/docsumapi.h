// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "docsumrequest.h"
#include "docsumreply.h"

namespace search::engine {

/**
 * A docsum client is the object being notified of the completion of
 * an asynchronous docsum operation.
 **/
class DocsumClient
{
public:
    /**
     * Invoked by the docsum server to indicate the completion of an
     * asynchronous docsum operation.
     *
     * @param reply the docsum reply
     **/
    virtual void getDocsumsDone(DocsumReply::UP reply) = 0;

    /**
     * Empty, needed for subclassing
     **/
    virtual ~DocsumClient() {}
};

/**
 * A docsum server is an object capable of performing a docsum
 * operation.
 **/
class DocsumServer
{
public:
    /**
     * Initiate a docsum operation that can be completed either
     * synchronously or asynchronously. The return value will indicate
     * whether the server selected to perform the operation
     * synchronously or asynchronously. If the return value contains
     * an object, then the operation completed synchronously and no
     * further action will be taken by the server. If the return value
     * did not contain an object, the operation will continue
     * asynchronously, and the given client will be notified when the
     * operation is completed. The server is not allowed to signal an
     * asynchronous completion of the operation in the context of this
     * method invocation.
     *
     * @return actual return value if sync, 'null' if async
     * @param request object containing request parameters.
     *        Note that it is decoded lazily -> upon access.
     * @param client the client to be notified of async completion
     **/

    virtual DocsumReply::UP getDocsums(DocsumRequest::Source request, DocsumClient &client) = 0;
    /**
     * As above but synchronous.
     * @param request object containing request parameters.
     * @return the response.
     **/
    virtual DocsumReply::UP getDocsums(DocsumRequest::UP request);

    /**
     * Empty, needed for subclassing
     **/
    virtual ~DocsumServer() {}
};

}
