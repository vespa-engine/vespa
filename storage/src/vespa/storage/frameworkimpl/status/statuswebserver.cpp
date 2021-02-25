// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "statuswebserver.h"
#include <vespa/storageframework/storageframework.h>
#include <vespa/vespalib/util/host_name.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/component/vtag.h>
#include <vespa/vespalib/net/crypto_engine.h>
#include <functional>

#include <vespa/log/log.h>
LOG_SETUP(".status");

namespace storage {

StatusWebServer::StatusWebServer(
        framework::ComponentRegister& componentRegister,
        framework::StatusReporterMap& reporterMap,
        const config::ConfigUri & configUri)
    : _reporterMap(reporterMap),
      _port(0),
      _httpServer(),
      _configFetcher(configUri.getContext()),
      _component(std::make_unique<framework::Component>(componentRegister, "Status"))
{
    _configFetcher.subscribe<vespa::config::content::core::StorStatusConfig>(configUri.getConfigId(), this);
    _configFetcher.start();
}

StatusWebServer::~StatusWebServer()
{
    // Avoid getting config during shutdown
    _configFetcher.close();

    if (_httpServer) {
        LOG(debug, "Shutting down status web server on port %u", _httpServer->getListenPort());
    }
    // Delete http server to ensure that no more incoming requests reach us.
    _httpServer.reset();
}

void StatusWebServer::configure(std::unique_ptr<vespa::config::content::core::StorStatusConfig> config)
{
    int newPort = config->httpport;
        // If server is already running, ignore config updates that doesn't
        // alter port, or suggests random port.
    if (_httpServer) {
        if (newPort == 0 || newPort == _port) return;
    }
        // Try to create new server before destroying old.
    LOG(info, "Starting status web server on port %u.", newPort);
    std::unique_ptr<WebServer> server;
        // Negative port number means don't run the web server
    if (newPort >= 0) {
        try {
            server = std::make_unique<WebServer>(*this, newPort);
        } catch (const vespalib::PortListenException & e) {
            LOG(error, "Failed listening to network port(%d) with protocol(%s): '%s', giving up and restarting.",
                e.get_port(), e.get_protocol().c_str(), e.what());
            std::_Exit(17);
        }
        // Now that we know config update went well, update internal state
        _port = server->getListenPort();
        LOG(config, "Status pages now available on port %u", _port);
        if (_httpServer) {
            LOG(debug, "Shutting down old status server.");
            _httpServer.reset();
            LOG(debug, "Done shutting down old status server.");
        }
    } else if (_httpServer) {
        LOG(info, "No longer running status server as negative port was given "
                  "in config, indicating not to run a server.");
    }
    _httpServer = std::move(server);
}

StatusWebServer::WebServer::WebServer(StatusWebServer& status, uint16_t port)
    : _status(status),
      _server(vespalib::Portal::create(vespalib::CryptoEngine::get_default(), port)),
      _executor(1, 256_Ki),
      _root(_server->bind("/", *this))
{
}

StatusWebServer::WebServer::~WebServer()
{
    _root.reset();
    _executor.shutdown().sync();    
}

namespace {

struct HandleGetTask : vespalib::Executor::Task {
    vespalib::Portal::GetRequest request;
    std::function<void(vespalib::Portal::GetRequest)> fun;
    HandleGetTask(vespalib::Portal::GetRequest request_in,
                  std::function<void(vespalib::Portal::GetRequest)> fun_in)
        : request(std::move(request_in)), fun(std::move(fun_in)) {}
    void run() override { fun(std::move(request)); }
};

}


void
StatusWebServer::WebServer::get(vespalib::Portal::GetRequest request)
{
    auto fun = [this](vespalib::Portal::GetRequest req)
               {
                   handle_get(std::move(req));
               };
    _executor.execute(std::make_unique<HandleGetTask>(std::move(request), std::move(fun)));
}

void
StatusWebServer::WebServer::handle_get(vespalib::Portal::GetRequest request)
{
    LOG(debug, "Status got get request '%s'", request.get_uri().c_str());
    framework::HttpUrlPath urlpath(request.get_path(), request.export_params(), request.get_host());
    _status.handlePage(urlpath, std::move(request));
}

namespace {
    class IndexPageReporter : public framework::HtmlStatusReporter {
        std::ostringstream ost;
        void reportHtmlStatus(std::ostream& out,const framework::HttpUrlPath&) const override {
            out << ost.str();
        }

    public:
        IndexPageReporter() : framework::HtmlStatusReporter("", "Index page") {}

        template<typename T>
        IndexPageReporter& operator<<(const T& t) { ost << t; return *this; }
    };
}


int
StatusWebServer::getListenPort() const
{
    return _httpServer ? _httpServer->getListenPort() : -1;
}

void
StatusWebServer::handlePage(const framework::HttpUrlPath& urlpath, vespalib::Portal::GetRequest request)
{
    vespalib::string link(urlpath.getPath());
    if (!link.empty() && link[0] == '/') link = link.substr(1);

    size_t slashPos = link.find('/');
    if (slashPos != std::string::npos) link = link.substr(0, slashPos);

    if ( ! link.empty()) {
        const framework::StatusReporter *reporter = _reporterMap.getStatusReporter(link);
        if (reporter != nullptr) {
            try {
                std::ostringstream content;
                auto content_type = reporter->getReportContentType(urlpath);
                if (reporter->reportStatus(content, urlpath)) {
                    request.respond_with_content(content_type, content.str());
                } else {
                    request.respond_with_error(404, "Not Found");
                }
            } catch (std::exception &e) {
                LOG(warning, "Internal Server Error: %s", e.what());
                request.respond_with_error(500, "Internal Server Error");
            }
        } else {
            request.respond_with_error(404, "Not Found");
        }
    } else {
        IndexPageReporter indexRep;
        indexRep << "<p><b>Binary version of Vespa:</b> "
                 << vespalib::Vtag::currentVersion.toString()
                 << "</p>\n";
        {
            for (const framework::StatusReporter * reporter : _reporterMap.getStatusReporters()) {
                indexRep << "<a href=\"" << reporter->getId() << "\">"
                         << reporter->getName() << "</a><br>\n";
            }
        }
        std::ostringstream content;
        auto content_type = indexRep.getReportContentType(urlpath);
        indexRep.reportStatus(content, urlpath);
        request.respond_with_content(content_type, content.str());
    }
    LOG(spam, "Status finished request");
}

} // storage
