// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "index_writer.h"
#include <vespa/document/fieldvalue/document.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.indexadapter");

using document::Document;

namespace proton {

IndexWriter::IndexWriter(const IIndexManager::SP &mgr)
    : _mgr(mgr)
{
}

IndexWriter::~IndexWriter() = default;

bool
IndexWriter::ignoreOperation(search::SerialNum serialNum) const {
    return (serialNum <= _mgr->getFlushedSerialNum());
}

void
IndexWriter::put(search::SerialNum serialNum, const document::Document &doc, const search::DocumentIdT lid, OnWriteDoneType on_write_done)
{
    if (ignoreOperation(serialNum)) {
        return;
    }
    ns_log::Logger::LogLevel level = getDebugLevel(lid, doc.getId());
    if (LOG_WOULD_VLOG(level)) {
        vespalib::string s1(doc.toString(true));
        VLOG(level, "Handle put: serial(%" PRIu64 "), docId(%s), lid(%u), document(sz=%ld)",
            serialNum, doc.getId().toString().c_str(), lid, s1.size());
        const size_t chunksize(30000);
        for (size_t accum(0); accum < s1.size(); accum += chunksize) {
            VLOG(level, "Handle put continued...: serial(%" PRIu64 "), docId(%s), lid(%u), document(sz=%ld{%ld, %ld}) {\n%.30000s\n}",
                serialNum, doc.getId().toString().c_str()+accum, lid, s1.size(), accum, std::min(accum+chunksize, s1.size()), s1.c_str());
        }
    }
    _mgr->putDocument(lid, doc, serialNum, on_write_done);
}

void
IndexWriter::removeDocs(search::SerialNum serialNum, LidVector lids)
{
    if (serialNum <= _mgr->getFlushedSerialNum()) {
        return;
    }
    for (search::DocumentIdT lid : lids) {
        VLOG(getDebugLevel(lid, NULL), "Handle remove: serial(%" PRIu64 "), num_lids(%lu)", serialNum, lids.size());
    }
    _mgr->removeDocuments(std::move(lids), serialNum);
}

void
IndexWriter::commit(search::SerialNum serialNum, OnWriteDoneType onWriteDone)
{
    if (serialNum <= _mgr->getFlushedSerialNum()) {
        return;
    }
    _mgr->commit(serialNum, onWriteDone);
}

void
IndexWriter::heartBeat(search::SerialNum serialNum)
{
    _mgr->heartBeat(serialNum);
}

void
IndexWriter::compactLidSpace(search::SerialNum serialNum, const search::DocumentIdT lid)
{
    if (serialNum <= _mgr->getFlushedSerialNum()) {
        return;
    }
    _mgr->compactLidSpace(lid, serialNum);
}

} // namespace proton
