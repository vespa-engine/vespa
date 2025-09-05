// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "monitoring_dump_iterator.h"
#include <vespa/log/log.h>
LOG_SETUP(".queryeval.monitoring_dump_iterator");

namespace search::queryeval {

MonitoringDumpIterator::MonitoringDumpIterator(MonitoringSearchIterator::UP iterator)
    : _search(std::move(iterator))
{
}

MonitoringDumpIterator::~MonitoringDumpIterator()
{
    MonitoringSearchIterator::Dumper dumper(4, 25, 7, 10, 6);
    visit(dumper, "", *_search);
    LOG(info, "Search stats: %s", dumper.toString().c_str());
}

void
MonitoringDumpIterator::doSeek(uint32_t docId)
{
    _search->seek(docId);
    setDocId(_search->getDocId());
}

void
MonitoringDumpIterator::doUnpack(uint32_t docId)
{
    _search->unpack(docId);
}

void
MonitoringDumpIterator::get_element_ids(uint32_t docid, std::vector<uint32_t>& element_ids)
{
    _search->get_element_ids(docid, element_ids);
    setDocId(_search->getDocId());
}

}
