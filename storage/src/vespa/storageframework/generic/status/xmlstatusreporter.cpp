// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "xmlstatusreporter.h"
#include <cassert>

namespace storage {
namespace framework {

XmlStatusReporter::XmlStatusReporter(vespalib::stringref id,
                                     vespalib::stringref name)
    : StatusReporter(id, name)
{
}

XmlStatusReporter::~XmlStatusReporter()
{
}

void
XmlStatusReporter::initXmlReport(vespalib::XmlOutputStream& xos,
                                 const HttpUrlPath&) const
{
    using namespace vespalib::xml;
    xos << XmlTag("status")
        << XmlAttribute("id", getId())
        << XmlAttribute("name", getName());
}

void
XmlStatusReporter::finalizeXmlReport(vespalib::XmlOutputStream& xos,
                                     const HttpUrlPath&) const
{
    using namespace vespalib::xml;
    xos << XmlEndTag();
    assert(xos.isFinalized());
}

vespalib::string
XmlStatusReporter::getReportContentType(const HttpUrlPath&) const
{
    return "application/xml";
}

bool
XmlStatusReporter::reportStatus(std::ostream& out,
                                const HttpUrlPath& path) const
{
    out << "<?xml version=\"1.0\"?>\n";
    vespalib::XmlOutputStream xos(out);
    initXmlReport(xos, path);
    vespalib::stringref failure = reportXmlStatus(xos, path);
    if (!failure.empty()) {
        using namespace vespalib::xml;
        xos << XmlContent("Failed to report XML status: " + failure);
    }
    finalizeXmlReport(xos, path);
    return failure.empty();
}

} // framework
} // storage
