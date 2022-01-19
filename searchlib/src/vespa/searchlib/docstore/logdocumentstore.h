// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "documentstore.h"
#include "logdatastore.h"
#include <vespa/searchlib/common/tunefileinfo.h>

namespace search {

namespace common { class FileHeaderContext; }

/**
 * Simple document store that contains serialized Document instances.
 * updates will be held in memory until flush() is called.
 * Uses a Local ID as key.
 **/
class LogDocumentStore : public DocumentStore
{
public:
    class Config : public DocumentStore::Config {
    public:
        Config() : DocumentStore::Config(), _logConfig() { }
        Config(const DocumentStore::Config & base, const LogDataStore::Config & log) :
            DocumentStore::Config(base),
            _logConfig(log)
        { }
        const LogDataStore::Config & getLogConfig() const { return _logConfig; }
        LogDataStore::Config & getLogConfig() { return _logConfig; }
        bool operator == (const Config & rhs) const;
        bool operator != (const Config & rhs) const { return ! (*this == rhs); }
    private:
        LogDataStore::Config _logConfig;
    };
    /**
     * Construct a document store.
     * If the "simpledocstore.dat" data file exists, reads meta-data (offsets) into memory.
     *
     * @throws vespalib::IoException if the file is corrupt or other IO problems occur.
     * @param docMan   The document type manager to use when deserializing.
     * @param baseDir  The path to a directory where "simpledocstore.dat" will exist.
     * @param fileHeaderContext The file header context used to populate
     *                          the generic file header with extra tags.
     *                          The caller must keep it alive for the semantic
     *                          lifetime of the log data store.
     */
    LogDocumentStore(vespalib::Executor & executor, const vespalib::string & baseDir, const Config & config,
                     const GrowStrategy & growStrategy, const TuneFileSummary &tuneFileSummary,
                     const common::FileHeaderContext &fileHeaderContext,
                     transactionlog::SyncProxy &tlSyncer, IBucketizer::SP bucketizer);
    ~LogDocumentStore() override;
    void reconfigure(const Config & config);
private:
    void compactBloat(uint64_t syncToken) override  { _backingStore.compactBloat(syncToken); }
    void compactSpread(uint64_t syncToken) override { _backingStore.compactSpread(syncToken); }
    LogDataStore _backingStore;
};

} // namespace search

