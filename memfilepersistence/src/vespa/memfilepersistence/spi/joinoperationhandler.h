// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::memfile::JoinHandler
 * \ingroup memfile
 */
#pragma once

#include <vespa/memfilepersistence/spi/operationhandler.h>
#include <vespa/persistence/spi/persistenceprovider.h>

namespace storage {

namespace memfile {

class JoinOperationHandler : public OperationHandler {
public:
    typedef std::unique_ptr<JoinOperationHandler> UP;

    JoinOperationHandler(Environment&);

    spi::Result join(const spi::Bucket& source1,
                     const spi::Bucket& source2,
                     const spi::Bucket& target);

    spi::Result singleJoin(const spi::Bucket& source,
                           const spi::Bucket& target);

private:
    Environment& _env;

    void copySlots(MemFile& source, MemFile& target);
    void clearBucketFromCache(const spi::Bucket&);
};

} // memfile
} // storage

