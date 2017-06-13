// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/base/documentid.h>
#include <vespa/document/bucket/bucketidfactory.h>

class Locator {
private:
    document::BucketIdFactory _factory;
    uint32_t                  _numColumns;

public:
    /**
     * Constructs a new locator object.
     */
    Locator(uint32_t numColumns = 0);

    /**
     * Configures this locator using the supplied configuration id and cluster name. This method will
     * subscribe to some known config and attempt to retrieve the number of columns of the given search
     * cluster from that.
     *
     * This method throws an exception if it could not be configured.
     *
     * @param configId    The config identifier to subscribe to.
     * @param clusterName The name of the search cluster to resolve locations in.
     */
    void configure(const std::string &configId,
                   const std::string &clusterName);

    /**
     * Returns the bucket id to which a document id belongs.
     *
     * @param docId The document id to resolve.
     * @return The corresponding bucket id.
     */
    document::BucketId getBucketId(document::DocumentId &docId);

    /**
     * Returns the column in which the given document id belongs.
     *
     * @param docId The document id to resolve.
     * @return The corresponding column.
     */
    uint32_t getSearchColumn(document::DocumentId &docId);
};

