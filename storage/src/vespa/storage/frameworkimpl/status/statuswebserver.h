// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::Status
 * @ingroup storageserver
 *
 * @brief Storage link handling status.
 *
 * @version $Id: status.h 126730 2011-09-30 14:02:22Z humbe $
 */

#pragma once

#include <vespa/storage/config/config-stor-status.h>
#include <vespa/storageframework/generic/thread/runnable.h>
#include <vespa/config/helper/ifetchercallback.h>
#include <vespa/vespalib/portal/portal.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <list>

namespace config {
    class ConfigUri;
    class ConfigFetcher;
}
namespace storage {

namespace framework {
    struct StatusReporter;
    struct StatusReporterMap;
    struct ThreadHandle;
    struct ComponentRegister;
    class Thread;
    class HttpUrlPath;
    class Component;
}

class StatusWebServer : private config::IFetcherCallback<vespa::config::content::core::StorStatusConfig>
{
    class WebServer : public vespalib::Portal::GetHandler {
        StatusWebServer&              _status;
        vespalib::Portal::SP          _server;
        vespalib::ThreadStackExecutor _executor;
        vespalib::Portal::Token::UP   _root;

    public:
        WebServer(StatusWebServer&, uint16_t port);
        ~WebServer();

        void get(vespalib::Portal::GetRequest request) override;
        void handle_get(vespalib::Portal::GetRequest request);

        int getListenPort() const {
            return _server->listen_port();
        }
    };
    struct HttpRequest {
        typedef std::shared_ptr<HttpRequest> SP;

        vespalib::string _url;
        vespalib::string _serverSpec;
        std::unique_ptr<vespalib::string> _result;

        HttpRequest(vespalib::stringref url, vespalib::stringref serverSpec)
            : _url(url),
              _serverSpec(serverSpec),
              _result()
        {}
    };

    framework::StatusReporterMap&          _reporterMap;
    uint16_t                               _port;
    std::unique_ptr<WebServer>             _httpServer;
    std::unique_ptr<config::ConfigFetcher> _configFetcher;
    std::unique_ptr<framework::Component>  _component;

public:
    StatusWebServer(const StatusWebServer &) = delete;
    StatusWebServer & operator = (const StatusWebServer &) = delete;
    StatusWebServer(framework::ComponentRegister&,
                    framework::StatusReporterMap&,
                    const config::ConfigUri & configUri);
    ~StatusWebServer() override;
    int getListenPort() const;
    void handlePage(const framework::HttpUrlPath&, vespalib::Portal::GetRequest request);
private:
    void invoke_reporter(const framework::StatusReporter&,
                         const framework::HttpUrlPath&,
                         vespalib::Portal::GetRequest&);
    void configure(std::unique_ptr<vespa::config::content::core::StorStatusConfig> config) override;
};

}
