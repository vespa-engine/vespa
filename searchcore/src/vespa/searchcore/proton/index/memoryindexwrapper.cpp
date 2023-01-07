// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "memoryindexwrapper.h"
#include <vespa/searchcorespi/index/indexsearchablevisitor.h>
#include <vespa/searchlib/common/serialnumfileheadercontext.h>
#include <vespa/searchlib/diskindex/indexbuilder.h>
#include <vespa/vespalib/util/exceptions.h>

using search::SerialNum;
using search::TuneFileIndexing;
using search::common::FileHeaderContext;
using search::common::SerialNumFileHeaderContext;
using search::diskindex::IndexBuilder;
using search::index::FieldLengthInfo;
using search::index::Schema;
using vespalib::IllegalStateException;

namespace proton {

MemoryIndexWrapper::MemoryIndexWrapper(const search::index::Schema& schema,
                                       const search::index::IFieldLengthInspector& inspector,
                                       const search::common::FileHeaderContext& fileHeaderContext,
                                       const TuneFileIndexing& tuneFileIndexing,
                                       searchcorespi::index::IThreadingService& threadingService,
                                       search::SerialNum serialNum)
    : _index(schema, inspector, threadingService.indexFieldInverter(),
             threadingService.indexFieldWriter()),
      _serialNum(serialNum),
      _fileHeaderContext(fileHeaderContext),
      _tuneFileIndexing(tuneFileIndexing)
{
}

void
MemoryIndexWrapper::flushToDisk(const vespalib::string &flushDir, uint32_t docIdLimit, SerialNum serialNum)
{
    const uint64_t numWords = _index.getNumWords();
    _index.freeze(); // TODO(geirst): is this needed anymore?
    IndexBuilder indexBuilder(_index.getSchema(), flushDir, docIdLimit);
    SerialNumFileHeaderContext fileHeaderContext(_fileHeaderContext, serialNum);
    indexBuilder.open(numWords, *this, _tuneFileIndexing, fileHeaderContext);
    _index.dump(indexBuilder);
    indexBuilder.close();
}

search::SerialNum
MemoryIndexWrapper::getSerialNum() const
{
    return _serialNum.load(std::memory_order_relaxed);
}

void
MemoryIndexWrapper::accept(searchcorespi::IndexSearchableVisitor &visitor) const
{
    visitor.visit(*this);
}

FieldLengthInfo
MemoryIndexWrapper::get_field_length_info(const vespalib::string& field_name) const
{
    return _index.get_field_length_info(field_name);
}

} // namespace proton

