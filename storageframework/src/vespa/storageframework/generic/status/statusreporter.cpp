// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "statusreporter.h"

namespace storage {
namespace framework {

StatusReporter::StatusReporter(vespalib::stringref id, vespalib::stringref name)
    : _id(id),
      _name(name)
{
}

StatusReporter::~StatusReporter()
{
}

bool
StatusReporter::reportHttpHeader(std::ostream& out,
                                 const HttpUrlPath& path) const
{
    vespalib::string contentType(getReportContentType(path));
    if (contentType == "") return false;
    out << "HTTP/1.1 200 OK\r\n"
           "Connection: Close\r\n"
           "Content-type: " << contentType << "\r\n\r\n";
    return true;
}

} // framework
} // storage
