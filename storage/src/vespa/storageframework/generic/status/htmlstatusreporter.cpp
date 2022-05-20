// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "htmlstatusreporter.h"

namespace storage::framework {

HtmlStatusReporter::HtmlStatusReporter(vespalib::stringref id,
                                       vespalib::stringref name)
    : StatusReporter(id, name)
{
}

HtmlStatusReporter::~HtmlStatusReporter() = default;

void
HtmlStatusReporter::reportHtmlHeader(std::ostream& out,
                                     const HttpUrlPath& path) const
{
    out << "<html>\n"
        << "<head>\n"
        << "  <title>" << getName() << "</title>\n";
    reportHtmlHeaderAdditions(out, path);
    out << "</head>\n"
        << "<body>\n"
        << "  <h1>" << getName() << "</h1>\n";
}

void
HtmlStatusReporter::reportHtmlFooter(std::ostream& out,
                                     const HttpUrlPath&) const
{
    out << "</body>\n</html>\n";
}

vespalib::string
HtmlStatusReporter::getReportContentType(const HttpUrlPath&) const
{
    return "text/html";
}

bool
HtmlStatusReporter::reportStatus(std::ostream& out,
                                 const HttpUrlPath& path) const
{
    if (!isValidStatusRequest()) return false;
    reportHtmlHeader(out, path);
    reportHtmlStatus(out, path);
    reportHtmlFooter(out, path);
    return true;
}

}
