// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchrequest.h"
#include "searchreply.h"

namespace search::engine {

/**
 * A search client is the object being notified of the completion of
 * an asynchronous search operation.
 **/
class SearchClient
{
public:
    /**
     * Invoked by the search server to indicate the completion of an
     * asynchronous search operation.
     *
     * @param reply the search reply
     **/
    virtual void searchDone(SearchReply::UP reply) = 0;

    /**
     * Empty, needed for subclassing
     **/
    virtual ~SearchClient() {}
};

/**
 * A search server is an object capable of performing a search
 * operation.
 **/
class SearchServer
{
public:
    /**
     * Initiate a search operation that can be completed either
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
     * @param request object containing request parameters
     * @param client the client to be notified of async completion
     **/
    virtual SearchReply::UP search(SearchRequest::Source request, SearchClient &client) = 0;

    /**
     * Empty, needed for subclassing
     **/
    virtual ~SearchServer() {}
};

}

