// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::memfile::OperationHandler
 * \ingroup memfile
 *
 * \brief Super class for operation handlers.
 *
 * The operation handler superclass provides common functionality needed to
 * operation handlers.
 */
#pragma once

#include <vespa/memfilepersistence/memfile/memfile.h>
#include <vespa/memfilepersistence/memfile/memfilecache.h>
#include <vespa/memfilepersistence/memfile/memfileptr.h>
#include <vespa/memfilepersistence/common/environment.h>
#include <vespa/memfilepersistence/common/filespecification.h>
#include <vespa/memfilepersistence/common/slotmatcher.h>
#include <vespa/memfilepersistence/common/types.h>
#include <vespa/persistence/spi/bucketinfo.h>
#include <vespa/document/fieldset/fieldsetrepo.h>

namespace document {
    namespace select {
        class Node;
    }
}

namespace storage {
namespace memfile {

class OperationHandler : protected Types
{
protected:
    Environment& _env;

public:
    typedef std::unique_ptr<OperationHandler> UP;

    OperationHandler(const OperationHandler &) = delete;
    OperationHandler & operator = (const OperationHandler &) = delete;
    OperationHandler(Environment&);
    virtual ~OperationHandler() {}

    struct ReadResult : private Types {
        ReadResult(Document::UP doc,
                   Timestamp ts)
            : _doc(std::move(doc)),
              _ts(ts) {};

        ReadResult(ReadResult&& other)
            : _doc(std::move(other._doc)),
              _ts(other._ts) {};

        Document::UP _doc;
        Timestamp _ts;

        Document::UP getDoc() { return std::move(_doc); }
    };

    ReadResult read(MemFile&,
                    const DocumentId&,
                    Timestamp maxTimestamp,
                    GetFlag getFlags) const;

    ReadResult read(MemFile&, Timestamp timestamp, GetFlag getFlags) const;

    enum RemoveType
    {
        ALWAYS_PERSIST_REMOVE,
        PERSIST_REMOVE_IF_FOUND
    };

    Types::Timestamp remove(MemFile&,
                            const DocumentId&,
                            Timestamp,
                            RemoveType);

    Types::Timestamp unrevertableRemove(MemFile&,
                                        const DocumentId&,
                                        Timestamp);

    void write(MemFile&, const Document& doc, Timestamp);

    bool update(MemFile&,
                 const Document& headerToOverwrite,
                 Timestamp newTime,
                 Timestamp existingTime = Timestamp(0));

    /**
     * Get the slots matching a given matcher.
     *
     * @return The timestamps of the matching slots, ordered in rising
     *         timestamp order.
     */
    std::vector<Timestamp> select(MemFile&, SlotMatcher&,
                                  uint32_t iteratorFlags,
                                  Timestamp fromTimestamp = Timestamp(0),
                                  Timestamp toTimestamp = Timestamp(0));

    /** Verify that a document id belongs to a given bucket. */
    void verifyBucketMapping(const DocumentId&, const BucketId&) const;

    MemFilePtr getMemFile(const spi::Bucket& b, bool keepInCache = true);

    MemFilePtr getMemFile(const document::BucketId& id, Directory& dir,
                          bool keepInCache = true);

    MemFilePtr getMemFile(const document::BucketId& id, uint16_t disk,
                          bool keepInCache = true);

    document::FieldSet::UP parseFieldSet(const std::string& fieldSet);

    std::unique_ptr<document::select::Node>
        parseDocumentSelection(const std::string& documentSelection,
                               bool allowLeaf);
};

} // memfile
} // storage

