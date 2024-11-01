// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "diskindexwrapper.h"
#include <vespa/searchcorespi/index/indexreadutilities.h>
#include <vespa/searchcorespi/index/indexsearchablevisitor.h>

using search::TuneFileSearch;
using search::diskindex::IPostingListCache;
using search::index::FieldLengthInfo;
using searchcorespi::index::IndexReadUtilities;

namespace proton {

DiskIndexWrapper::DiskIndexWrapper(const std::string &indexDir,
                                   const TuneFileSearch &tuneFileSearch,
                                   std::shared_ptr<IPostingListCache> posting_list_cache,
                                   size_t cacheSize)
    : _index(indexDir, std::move(posting_list_cache), cacheSize),
      _serialNum(0)
{
    bool setupIndexOk = _index.setup(tuneFileSearch);
    assert(setupIndexOk);
    (void) setupIndexOk;
    _serialNum = IndexReadUtilities::readSerialNum(indexDir);
}

DiskIndexWrapper::DiskIndexWrapper(const DiskIndexWrapper &oldIndex,
                                   const TuneFileSearch &tuneFileSearch,
                                   size_t cacheSize)
    : _index(oldIndex._index.getIndexDir(), oldIndex._index.get_posting_list_cache(), cacheSize),
      _serialNum(0)
{
    bool setupIndexOk = _index.setup(tuneFileSearch, oldIndex._index);
    assert(setupIndexOk);
    (void) setupIndexOk;
    _serialNum = oldIndex.getSerialNum();
}

search::SerialNum
DiskIndexWrapper::getSerialNum() const
{
    return _serialNum;
}

void
DiskIndexWrapper::accept(searchcorespi::IndexSearchableVisitor &visitor) const
{
    visitor.visit(*this);
}

FieldLengthInfo
DiskIndexWrapper::get_field_length_info(const std::string& field_name) const
{
    return _index.get_field_length_info(field_name);
}

}  // namespace proton

