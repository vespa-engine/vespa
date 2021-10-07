// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::framework::HtmlStatusReporter
 * \ingroup component
 *
 * \brief Specialization of StatusReporter for reporters of HTML data.
 *
 * To avoid code duplication, and to let all HTML status reporters be able
 * to look consistently, this specialization exist to have a common place to
 * implement common HTML parts printed.
 *
 * Note: If you want to write HTTP from a status reporter that can also write
 * other types of content, best practise is to instantiate the
 * PartlyHtmlStatusReporter to print the HTML headers and footers.
 */

#pragma once

#include "statusreporter.h"

namespace storage::framework {

struct HtmlStatusReporter : public StatusReporter {
    HtmlStatusReporter(vespalib::stringref id, vespalib::stringref name);
    virtual ~HtmlStatusReporter();

    /**
     * The default HTML header writer uses this function to allow page to add
     * some code in the <head></head> part of the HTML, such as javascript
     * functions.
     */
    virtual void reportHtmlHeaderAdditions(std::ostream&,
                                           const HttpUrlPath&) const {}

    /**
     * Write a default HTML header. It writes the start of an HTML
     * file, including a body statement and a header with component name.
     */
    virtual void reportHtmlHeader(std::ostream&, const HttpUrlPath&) const;

    /** Overwrite to write the actual HTML content. */
    virtual void reportHtmlStatus(std::ostream&, const HttpUrlPath&) const = 0;

    /** Writes a default HTML footer. Includes closing the body tag. */
    virtual void reportHtmlFooter(std::ostream&, const HttpUrlPath&) const;

    // Implementation of StatusReporter interface
    vespalib::string getReportContentType(const HttpUrlPath&) const override;
    bool reportStatus(std::ostream&, const HttpUrlPath&) const override;
};

/**
 * This class can be used if your status reporter only reports HTML in some
 * instances. Then you can create an instance of this class in order to write
 * the HTML headers and footers when needed.
 */
struct PartlyHtmlStatusReporter : public HtmlStatusReporter {
    PartlyHtmlStatusReporter(const StatusReporter& main)
        : HtmlStatusReporter(main.getId(), main.getName()) {}

    void reportHtmlStatus(std::ostream&, const HttpUrlPath&) const override {}
};

}
