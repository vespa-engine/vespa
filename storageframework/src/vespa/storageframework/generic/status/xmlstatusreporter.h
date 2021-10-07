// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::framework::XmlStatusReporter
 * \ingroup component
 *
 * \brief Specialization of StatusReporter for reporters of XML data.
 *
 * To make it easy to write legal XML and escape content that needs to be
 * escaped, an XML writer is used to write the actual XML data.
 *
 * Note: If you want to write XML from a status reporter that can also write
 * other types of content, best practise is to implement StatusReporter, and if
 * serving XML in the reportStatus function, create a temporary
 * XmlStatusReporter object, in order to reuse the report functions to init
 * and finalize XML writing.
 */

#pragma once

#include "statusreporter.h"
#include <vespa/vespalib/util/xmlstream.h>

namespace storage::framework {

struct XmlStatusReporter : public StatusReporter {
    XmlStatusReporter(vespalib::stringref id, vespalib::stringref name);
    virtual ~XmlStatusReporter();

    virtual void initXmlReport(vespalib::xml::XmlOutputStream&,
                               const HttpUrlPath&) const;

    /**
     * @return Empty string if ok, otherwise indicate a failure condition.
     */
    virtual vespalib::string reportXmlStatus(vespalib::xml::XmlOutputStream&,
                                             const HttpUrlPath&) const = 0;

    virtual void finalizeXmlReport(vespalib::xml::XmlOutputStream&,
                                   const HttpUrlPath&) const;

    // Implementation of status reporter interface
    vespalib::string getReportContentType(const HttpUrlPath&) const override;
    bool reportStatus(std::ostream&, const HttpUrlPath&) const override;
};

/**
 * If you're only reporting XML in some cases, you can use this instance to
 * wrap the actual XML parts, so you can reuse the code that outputs the XML.
 * Just use output operator in this class to add the actual XML.
 */
class PartlyXmlStatusReporter : public XmlStatusReporter {
    vespalib::XmlOutputStream _xos;
    const HttpUrlPath& _path;

public:
    PartlyXmlStatusReporter(const StatusReporter& main, std::ostream& out,
                            const HttpUrlPath& path)
        : XmlStatusReporter(main.getId(), main.getName()),
          _xos(out),
          _path(path)
    {
        initXmlReport(_xos, path);
    }

    ~PartlyXmlStatusReporter() {
        finalizeXmlReport(_xos, _path);
    }

    vespalib::XmlOutputStream& getStream() { return _xos; }
    vespalib::string reportXmlStatus(vespalib::xml::XmlOutputStream&, const HttpUrlPath&) const override { return ""; }

    template<typename T>
    PartlyXmlStatusReporter& operator<<(const T& v) {
        _xos << v;
        return *this;
    }
};

}
