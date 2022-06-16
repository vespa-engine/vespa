// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "visitorstatistics.h"
#include <ostream>

namespace vdslib {

VisitorStatistics::VisitorStatistics()
    : _bucketsVisited(0),
      _documentsVisited(0),
      _bytesVisited(0),
      _documentsReturned(0),
      _bytesReturned(0)
{
}

VisitorStatistics
VisitorStatistics::operator+(const VisitorStatistics& other) {
    VisitorStatistics vs;
    vs.setBucketsVisited(_bucketsVisited + other._bucketsVisited);
    vs.setDocumentsVisited(_documentsVisited + other._documentsVisited);
    vs.setBytesVisited(_bytesVisited + other._bytesVisited);
    vs.setDocumentsReturned(_documentsReturned + other._documentsReturned);
    vs.setBytesReturned(_bytesReturned + other._bytesReturned);
    return vs;
}

void
VisitorStatistics::print(std::ostream& out, bool, const std::string& indent) const
{
    out << indent << "Buckets visited: " << _bucketsVisited << "\n";
    out << indent << "Documents visited: " << _documentsVisited << "\n";
    out << indent << "Bytes visited: " << _bytesVisited << "\n";
    out << indent << "Documents returned: " << _documentsReturned << "\n";
    out << indent << "Bytes returned: " << _bytesReturned << "\n";
}

}
