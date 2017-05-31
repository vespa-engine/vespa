// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
#include <vespa/config/config.h>
#include <vespa/config/helper/configfetcher.h>
#include <vespa/fastlib/net/httpserver.h>
#include <list>

namespace storage {

namespace framework {
    class StatusReporterMap;
    class ThreadHandle;
    class ComponentRegister;
    class Thread;
    class HttpUrlPath;
    class Component;
}
class StatusWebServer : private config::IFetcherCallback<vespa::config::content::core::StorStatusConfig>,
                        private framework::Runnable
{
    class WebServer : public Fast_HTTPServer {
        StatusWebServer& _status;
        vespalib::string _serverSpec;

    public:
        WebServer(StatusWebServer&, uint16_t port);

        void onGetRequest(const string & url, const string & serverSpec, Fast_HTTPConnection& conn) override;
        const vespalib::string &getServerSpec() const {
            return _serverSpec;
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
    vespalib::Monitor                      _workerMonitor;
    uint16_t                               _port;
    std::unique_ptr<WebServer>             _httpServer;
    config::ConfigFetcher                  _configFetcher;
    std::list<HttpRequest::SP>             _queuedRequests;
    std::unique_ptr<framework::Component>  _component;
    std::unique_ptr<framework::Thread>     _thread;

public:
    StatusWebServer(const StatusWebServer &) = delete;
    StatusWebServer & operator = (const StatusWebServer &) = delete;
    StatusWebServer(framework::ComponentRegister&,
                    framework::StatusReporterMap&,
                    const config::ConfigUri & configUri);
    ~StatusWebServer() override;

    void handlePage(const framework::HttpUrlPath&, std::ostream& out);
    static vespalib::string getServerSpec(const vespalib::string &requestSpec,
                                          const vespalib::string &serverSpec);
private:
    void configure(std::unique_ptr<vespa::config::content::core::StorStatusConfig> config) override;
    void getPage(const char* url, Fast_HTTPConnection& conn);
    void run(framework::ThreadHandle&) override;
};

}
