// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.index.memoryindexwrapper");

#include "memoryindexwrapper.h"
#include <vespa/searchlib/common/serialnumfileheadercontext.h>
#include <vespa/searchlib/diskindex/indexbuilder.h>
#include <vespa/vespalib/util/exceptions.h>

using search::TuneFileIndexing;
using search::common::FileHeaderContext;
using search::common::SerialNumFileHeaderContext;
using search::index::Schema;
using search::diskindex::IndexBuilder;
using search::SerialNum;
using vespalib::IllegalStateException;

namespace proton {

MemoryIndexWrapper::MemoryIndexWrapper(const search::index::Schema &schema,
                                       const search::common::FileHeaderContext &fileHeaderContext,
                                       const TuneFileIndexing &tuneFileIndexing,
                                       searchcorespi::index::IThreadingService &
                                        threadingService)
    : _index(schema, threadingService.indexFieldInverter(),
             threadingService.indexFieldWriter()),
      _fileHeaderContext(fileHeaderContext),
      _tuneFileIndexing(tuneFileIndexing)
{
}

void
MemoryIndexWrapper::flushToDisk(const vespalib::string &flushDir,
                                uint32_t docIdLimit,
                                SerialNum serialNum)
{
    const uint64_t numWords = _index.getNumWords();
    _index.freeze(); // TODO(geirst): is this needed anymore?
    IndexBuilder indexBuilder(_index.getSchema());
    indexBuilder.setPrefix(flushDir);
    SerialNumFileHeaderContext fileHeaderContext(_fileHeaderContext,
                                                 serialNum);
    indexBuilder.open(docIdLimit, numWords, _tuneFileIndexing, fileHeaderContext);
    _index.dump(indexBuilder);
    indexBuilder.close();
}

} // namespace proton

