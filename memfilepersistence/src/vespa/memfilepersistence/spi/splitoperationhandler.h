// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::memfile::SplitHandler
 * \ingroup memfile
 *
 * \brief Class used to do basic operations to memfiles.
 */
#pragma once

#include <vespa/memfilepersistence/spi/operationhandler.h>
#include <vespa/persistence/spi/persistenceprovider.h>

namespace storage {

namespace memfile {

class SplitOperationHandler : public OperationHandler {
public:
    typedef std::unique_ptr<SplitOperationHandler> UP;

    SplitOperationHandler(Environment&);

    spi::Result split(const spi::Bucket& source,
                      const spi::Bucket& target1,
                      const spi::Bucket& target2);

private:
    /**
     * Copies the slots designated by the given list of timestamps from one mem
     * file to another.  If the target already has a slot at any of the given
     * timestamps, those timestamps aren't copied.
     */
    void copyTimestamps(const MemFile& source, MemFile& target,
                        const std::vector<Timestamp>& timestamps);

    uint32_t splitIntoFile(MemFile& source, const spi::Bucket& target);
};

} // memfile
} // storage

