// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Internal command used by visitor and filestor framework to gather partial
 * status from message processing threads.
 */

#pragma once

#include <vespa/storageapi/message/internal.h>
#include <vespa/storageframework/generic/status/httpurlpath.h>

namespace storage {

/**
 * @class RequestStatusPage
 * @ingroup visiting
 *
 * @brief Used to retrieve status page from threads.
 */
class RequestStatusPage : public api::InternalCommand {
    framework::HttpUrlPath _path;
    std::string _sortToken; // Used if sending multiple messages, to set order
                            // in which results should be sorted on status page.
                            // (Used by filestor threads)
public:
    static constexpr uint32_t ID = 2100;

    RequestStatusPage(const framework::HttpUrlPath& path);
    ~RequestStatusPage();

    const std::string& getSortToken() const { return _sortToken; }
    void setSortToken(const std::string& token) { _sortToken = token; }

    std::unique_ptr<api::StorageReply> makeReply() override;

    const framework::HttpUrlPath& getPath() const { return _path; }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

/**
 * @class RequestStatusPageReply
 * @ingroup visiting
 */
class RequestStatusPageReply : public api::InternalReply {
    std::string _status;
    std::string _sortToken;
public:
    static constexpr uint32_t ID = 2101;

    RequestStatusPageReply(const RequestStatusPage& cmd, const std::string& status);
    ~RequestStatusPageReply();

    const std::string& getStatus() const { return _status; }
    const std::string& getSortToken() const { return _sortToken; }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

struct StatusReqSorter {
    bool operator()(const std::shared_ptr<RequestStatusPageReply>& a,
                    const std::shared_ptr<RequestStatusPageReply>& b)
    {
        return (a->getSortToken() < b->getSortToken());
    }
};

} // storage

