// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/util/printable.h>

namespace vdslib {

class VisitorStatistics : public document::Printable
{
public:
    VisitorStatistics();

    VisitorStatistics operator+(const VisitorStatistics& other);

    uint32_t getBucketsVisited() const { return _bucketsVisited; }
    void setBucketsVisited(uint32_t bucketsVisited) { _bucketsVisited = bucketsVisited; }

    uint64_t getDocumentsVisited() const { return _documentsVisited; }
    void setDocumentsVisited(uint32_t documentsVisited) { _documentsVisited = documentsVisited; }

    uint64_t getBytesVisited() const { return _bytesVisited; }
    void setBytesVisited(uint32_t bytesVisited) { _bytesVisited = bytesVisited; }

    uint64_t getDocumentsReturned() const { return _documentsReturned; }
    void setDocumentsReturned(uint32_t documentsReturned) { _documentsReturned = documentsReturned; }

    uint64_t getBytesReturned() const { return _bytesReturned; }
    void setBytesReturned(uint32_t bytesReturned) { _bytesReturned = bytesReturned; }

    uint64_t getSecondPassDocumentsReturned() const { return _secondPassDocumentsReturned; }
    void setSecondPassDocumentsReturned(uint32_t documentsReturned) { _secondPassDocumentsReturned = documentsReturned; }

    uint64_t getSecondPassBytesReturned() const { return _secondPassBytesReturned; }
    void setSecondPassBytesReturned(uint32_t bytesReturned) { _secondPassBytesReturned = bytesReturned; }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
private:
    uint32_t _bucketsVisited;
    uint64_t _documentsVisited;
    uint64_t _bytesVisited;
    uint64_t _documentsReturned;
    uint64_t _bytesReturned;
    uint64_t _secondPassDocumentsReturned;
    uint64_t _secondPassBytesReturned;
};

}

