// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "statuswebserver.h"
#include <vespa/storageframework/generic/component/component.h>
#include <vespa/storageframework/generic/status/htmlstatusreporter.h>
#include <vespa/storageframework/generic/status/statusreportermap.h>
#include <vespa/vespalib/util/host_name.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/component/vtag.h>
#include <vespa/vespalib/net/connection_auth_context.h>
#include <vespa/vespalib/net/crypto_engine.h>
#include <vespa/vespalib/net/tls/statistics.h>
#include <vespa/config/subscription/configuri.h>
#include <vespa/config/helper/configfetcher.hpp>
#include <functional>

#include <vespa/log/log.h>
LOG_SETUP(".status");

namespace {
    VESPA_THREAD_STACK_TAG(status_web_server);
}
namespace storage {

StatusWebServer::StatusWebServer(
        framework::ComponentRegister& componentRegister,
        framework::StatusReporterMap& reporterMap,
        const config::ConfigUri & configUri)
    : _reporterMap(reporterMap),
      _port(0),
      _httpServer(),
      _configFetcher(std::make_unique<config::ConfigFetcher>(configUri.getContext())),
      _component(std::make_unique<framework::Component>(componentRegister, "Status"))
{
    _configFetcher->subscribe<vespa::config::content::core::StorStatusConfig>(configUri.getConfigId(), this);
    _configFetcher->start();
}

StatusWebServer::~StatusWebServer()
{
    // Avoid getting config during shutdown
    _configFetcher->close();

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
      _executor(1, status_web_server),
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
    IndexPageReporter();
    ~IndexPageReporter() override;

    template<typename T>
    IndexPageReporter& operator<<(const T& t) { ost << t; return *this; }
};

IndexPageReporter::IndexPageReporter() : framework::HtmlStatusReporter("", "Index page") {}
IndexPageReporter::~IndexPageReporter() = default;

}


int
StatusWebServer::getListenPort() const
{
    return _httpServer ? _httpServer->getListenPort() : -1;
}

void
StatusWebServer::invoke_reporter(const framework::StatusReporter& reporter,
                                 const framework::HttpUrlPath& url_path,
                                 vespalib::Portal::GetRequest& request)
{
    try {
        std::ostringstream content;
        auto content_type = reporter.getReportContentType(url_path);
        if (reporter.reportStatus(content, url_path)) {
            request.respond_with_content(content_type, content.str());
        } else {
            request.respond_with_error(404, "Not Found");
        }
    } catch (std::exception &e) {
        LOG(warning, "Internal Server Error: %s", e.what());
        request.respond_with_error(500, "Internal Server Error");
    }
}

void
StatusWebServer::handlePage(const framework::HttpUrlPath& urlpath, vespalib::Portal::GetRequest request)
{
    vespalib::string link(urlpath.getPath());

    // We allow a fixed path prefix that aliases down to whatever is provided after the prefix.
    vespalib::stringref optional_status_path_prefix = "/contentnode-status/v1/";
    if (link.starts_with(optional_status_path_prefix)) {
        link = link.substr(optional_status_path_prefix.size());
    }

    if (!link.empty() && link[0] == '/') {
        link = link.substr(1);
    }

    size_t slashPos = link.find('/');
    if (slashPos != std::string::npos) {
        link = link.substr(0, slashPos);
    }

    if ( ! link.empty()) {
        const framework::StatusReporter *reporter = _reporterMap.getStatusReporter(link);
        if (reporter != nullptr) {
            const auto& auth_ctx = request.auth_context();
            if (auth_ctx.capabilities().contains_all(reporter->required_capabilities())) {
                invoke_reporter(*reporter, urlpath, request);
            } else {
                vespalib::net::tls::CapabilityStatistics::get().inc_status_capability_checks_failed();
                // TODO should print peer address as well; not currently exposed
                LOG(warning, "Peer with %s denied status page access to '%s' due to insufficient "
                             "credentials (had %s, needed %s)",
                    auth_ctx.peer_credentials().to_string().c_str(),
                    link.c_str(), auth_ctx.capabilities().to_string().c_str(),
                    reporter->required_capabilities().to_string().c_str());
                request.respond_with_error(403, "Forbidden");
            }
        } else {
            request.respond_with_error(404, "Not Found");
        }
    } else {
        // TODO should the index page be capability-restricted? Would be a bit strange if the root
        //  index '/' page requires status capabilities but '/metrics' does not.
        //  The index page only leaks the Vespa version and node type (inferred by reporter set).
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
