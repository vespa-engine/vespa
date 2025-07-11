// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentmetastoreinitializer.h"
#include "documentmetastore.h"
#include <vespa/searchlib/common/indexmetainfo.h>
#include <vespa/searchcore/proton/common/eventlogger.h>
#include <vespa/searchcore/proton/common/memory_usage_logger.h>
#include <vespa/vespalib/util/exceptions.h>
#include <filesystem>

using search::GrowStrategy;
using search::IndexMetaInfo;
using vespalib::IllegalStateException;
using proton::initializer::InitializerTask;
using vespalib::make_string;

namespace proton::documentmetastore {

DocumentMetaStoreInitializer::
DocumentMetaStoreInitializer(const std::string baseDir,
                             const std::string &subDbName,
                             const std::string &docTypeName,
                             std::shared_ptr<DocumentMetaStore> dms)
    : _baseDir(baseDir),
      _subDbName(subDbName),
      _docTypeName(docTypeName),
      _dms(std::move(dms))
{ }

namespace {
std::string
failedMsg(const char * msg) {
    return make_string("Failed to load document meta store for document type '%s' from disk", msg);
}
}

void
DocumentMetaStoreInitializer::run()
{
    std::string name = DocumentMetaStore::getFixedName();
    IndexMetaInfo info(_baseDir);
    if (info.load()) {
        IndexMetaInfo::Snapshot snap = info.getBestSnapshot();
        if (snap.valid) {
            std::string attrFileName = _baseDir + "/" + snap.dirName + "/" + name;
            _dms->setBaseFileName(attrFileName);
            assert(_dms->hasLoadData());
            vespalib::Timer stopWatch;
            EventLogger::loadDocumentMetaStoreStart(_subDbName);
            MemoryUsageLogger::log("start load documentmetastore", _subDbName);
            if (!_dms->load()) {
                throw IllegalStateException(failedMsg(_docTypeName.c_str()));
            } else {
                _dms->commit(search::CommitParam(snap.syncToken));
            }
            EventLogger::loadDocumentMetaStoreComplete(_subDbName, stopWatch.elapsed());
            MemoryUsageLogger::log("finished load documentmetastore", _subDbName);
        }
    } else {
        std::filesystem::create_directory(std::filesystem::path(_baseDir));
        info.save();
    }
}

}
