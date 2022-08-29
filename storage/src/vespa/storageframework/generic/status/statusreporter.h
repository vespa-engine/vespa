// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::framework::StatusReporter
 * \ingroup component
 *
 * \brief Interface to implement for status reporters.
 *
 * Components that wants to make status pages available can implement this
 * interface in order to provide status information without depending on how
 * this information is server. Status data is typically available through an
 * HTTP server running in the process.
 *
 * Specializations of this interface exists for HTML and XML outputters.
 */
#pragma once

#include <ostream>
#include <vespa/storageframework/generic/status/httpurlpath.h>
#include <vespa/vespalib/net/tls/capability_set.h>
#include <vespa/vespalib/stllike/string.h>

namespace storage::framework {

struct StatusReporter {
    using Capability    = vespalib::net::tls::Capability;
    using CapabilitySet = vespalib::net::tls::CapabilitySet;

    StatusReporter(vespalib::stringref id, vespalib::stringref name);
    virtual ~StatusReporter();

    /**
     * Get the identifier. The identifier is a string matching regex
     * ^[A-Za-z0-9_]+$. It is used to identify the status page in contexts where
     * special characters are not wanted, such as in an URL.
     */
    const vespalib::string& getId() const { return _id; }
    /**
     * Get the descriptive name of the status reported. This string should be
     * able to contain anything.
     */
    const vespalib::string& getName() const { return _name; }

    virtual bool isValidStatusRequest() const { return true; }

    /**
     * By default, a status reporter requires the "vespa.content.status_pages" client capability.
     * This can be overridden by subclasses to require reporter-specific capabilities
     * (or none at all). If the client does not satisfy the required capabilities, a
     * "403 Forbidden" error response will be returned to the client.
     */
    virtual CapabilitySet required_capabilities() const noexcept {
        return CapabilitySet::of({ Capability::content_status_pages() });
    }

    /**
     * Called to get content type.
     * An empty string indicates page not found.
     */
    virtual vespalib::string getReportContentType(const HttpUrlPath&) const = 0;

    /**
     * Called to get the actual content to return in the status request.
     * @return False if no such page exist, in which case you should not have
     *         written to the output stream.
     */
    virtual bool reportStatus(std::ostream&, const HttpUrlPath&) const = 0;

private:
    vespalib::string _id;
    vespalib::string _name;

};

}
