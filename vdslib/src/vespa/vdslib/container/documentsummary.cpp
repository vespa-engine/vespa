// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentsummary.h"
#include <vespa/vespalib/util/growablebytebuffer.h>
#include <vespa/document/util/bytebuffer.h>
#include <vespa/vespalib/util/size_literals.h>
#include <algorithm>

namespace vdslib {

DocumentSummary::DocumentSummary() :
    _summaryBuffer(),
    _summary(),
    _summarySize(0)
{
    _summaryBuffer.reset(new vespalib::MallocPtr(4_Ki));
}

DocumentSummary::DocumentSummary(document::ByteBuffer& buf) :
    _summaryBuffer(),
    _summary(),
    _summarySize(0)
{
    deserialize(buf);
}

DocumentSummary::~DocumentSummary() = default;

void DocumentSummary::deserialize(document::ByteBuffer& buf)
{
    int32_t tmp;
    buf.getIntNetwork(tmp); // Just get dummy 4 byte field, to avoid handling different versions.
    uint32_t numResults(0);
    buf.getIntNetwork(tmp); numResults = tmp;
    _summary.resize(numResults);
    if (numResults > 0) {
        buf.getIntNetwork(tmp); _summarySize = tmp;
        _summaryBuffer.reset(new vespalib::MallocPtr(getSummarySize()));
        buf.getBytes(_summaryBuffer->str(), _summaryBuffer->size());

        const char * summarybp(_summaryBuffer->c_str());
        for (size_t n(0), p(0), m(_summary.size()); n < m; n++) {
            uint32_t sz(0);
            buf.getIntNetwork(tmp); sz = tmp;
            size_t oldP(p);
            while (summarybp[p++]) { }
            _summary[n] = Summary(oldP, p, sz);
            p += sz;
        }
    }
}

void DocumentSummary::serialize(vespalib::GrowableByteBuffer& buf) const
{
    buf.putInt(0); // Just serialize dummy 4 byte field, to avoid versioning.
    buf.putInt(_summary.size());
    if ( ! _summary.empty() ) {
        buf.putInt(getSummarySize());
        for (size_t i(0), m(_summary.size()); i < m; i++) {
            Summary s(_summary[i]);
            buf.putBytes(s.getDocId(_summaryBuffer->c_str()), s.getTotalSize());
        }
        for (size_t i(0), m(_summary.size()); i < m; i++) {
            buf.putInt(_summary[i].getSummarySize());
        }
    }
}

uint32_t DocumentSummary::getSerializedSize() const
{
    return (! _summary.empty()) ? 4*(3 + getSummaryCount()) + getSummarySize() : 8;
}

void DocumentSummary::addSummary(const char *docId, const void * buf, size_t sz)
{
    const size_t idSize = strlen(docId) + 1;
    Summary s(getSummarySize(), getSummarySize() + idSize, sz);
    _summary.push_back(s);
    const size_t end(getSummarySize() + idSize + sz);
    if (end > _summaryBuffer->size()) {
        _summaryBuffer->realloc(end*2);
    }
    memcpy(_summaryBuffer->str() + getSummarySize(), docId, idSize);
    memcpy(_summaryBuffer->str() + getSummarySize()+idSize, buf, sz);
    _summarySize = end;
}

void DocumentSummary::sort()
{
    std::sort(_summary.begin(), _summary.end(), Compare(_summaryBuffer->c_str()));
}

}

