// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "statuswebserver.h"
#include <vespa/storageframework/storageframework.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/fastlib/net/url.h>
#include <vespa/vespalib/util/host_name.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/component/vtag.h>

#include <vespa/log/log.h>
LOG_SETUP(".status");

namespace storage {

StatusWebServer::StatusWebServer(
        framework::ComponentRegister& componentRegister,
        framework::StatusReporterMap& reporterMap,
        const config::ConfigUri & configUri)
    : _reporterMap(reporterMap),
      _workerMonitor(),
      _port(0),
      _httpServer(),
      _configFetcher(configUri.getContext()),
      _queuedRequests(),
      _component(std::make_unique<framework::Component>(componentRegister, "Status")),
      _thread()
{
    _configFetcher.subscribe<vespa::config::content::core::StorStatusConfig>(configUri.getConfigId(), this);
    _configFetcher.start();
    framework::MilliSecTime maxProcessingTime(60 * 60 * 1000);
    framework::MilliSecTime maxWaitTime(10 * 1000);
    _thread = _component->startThread(*this, maxProcessingTime, maxWaitTime);

}

StatusWebServer::~StatusWebServer()
{
    // Avoid getting config during shutdown
    _configFetcher.close();

    if (_httpServer.get() != 0) {
        LOG(debug, "Shutting down status web server on port %u", _httpServer->getListenPort());
    }
    // Delete http server to ensure that no more incoming requests reach us.
    _httpServer.reset(0);

    // Stop internal thread such that we don't process anymore web requests
    if (_thread.get() != 0) {
        _thread->interruptAndJoin(&_workerMonitor);
    }
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
        server.reset(new WebServer(*this, newPort));
        server->SetKeepAlive(false);

        bool started = false;
        switch (server->Start()) {
            case FASTLIB_SUCCESS:
                started = true;
                break;
            case FASTLIB_HTTPSERVER_BADLISTEN:
                LOG(warning, "Listen failed on port %u", newPort);
                break;
            case FASTLIB_HTTPSERVER_NEWTHREADFAILED:
                LOG(warning, "Failed starting thread for status server on port %u", newPort);
                break;
            case FASTLIB_HTTPSERVER_ALREADYSTARTED:
                LOG(warning, "Failed starting status server on port %u (already started?)", newPort);
                break;
            default:
                LOG(warning, "Failed starting status server on port %u (unknown reason)", newPort);
                break;
        }
        if (!started) {
            std::ostringstream ost;
            ost << "Failed to start status HTTP server using port " << newPort << ".";
            if (_httpServer) {
                ost << " Status server still running on port " << _port << " instead of suggested port " << newPort;
            }
            LOG(fatal, "%s.", ost.str().c_str());
            _component->requestShutdown(ost.str());
            _httpServer.reset();
            return;
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
    : Fast_HTTPServer(port, NULL, 100, false, 128*1024, 10),
      _status(status),
      _serverSpec(vespalib::make_string("%s:%d", vespalib::HostName::get().c_str(), port))
{
}


namespace {
    /** Utility class for printing HTTP errors. */
    struct HttpErrorWriter {
        std::ostream& _out;

        HttpErrorWriter(std::ostream& out, vespalib::stringref error)
            : _out(out)
        {
            _out << "HTTP/1.1 " << error << "\r\n"
                    "Connection: Close\r\n"
                    "Content-type: text/html\r\n\r\n"
                    "<html><head><title>" << error << "</title></head>\r\n"
                    "<body><h1>" << error << "</h1>\r\n"
                    "<p>";
        }

        template<typename T>
        HttpErrorWriter& operator<<(const T& t) {
            _out << t;
            return *this;
        }

        ~HttpErrorWriter() {
            _out << "</p></body>\r\n"
                    "</html>\r\n";
        }
    };
}

void
StatusWebServer::WebServer::onGetRequest(const string & tmpurl, const string &serverSpec, Fast_HTTPConnection& conn)
{
    Fast_URL urlCodec;
    int bufLength = tmpurl.length() * 2 + 10;
    char * encodedUrl = new char[bufLength];
    strcpy(encodedUrl, tmpurl.c_str());
    char decodedUrl[bufLength];
    urlCodec.DecodeQueryString(encodedUrl);
    urlCodec.decode(encodedUrl, decodedUrl, bufLength);
    delete [] encodedUrl;

    string url = decodedUrl;

    LOG(debug, "Status got get request '%s'", url.c_str());
    framework::HttpUrlPath urlpath(url.c_str(),
            StatusWebServer::getServerSpec(serverSpec, getServerSpec()));
    std::string link(urlpath.getPath());
    if (link.size() > 0 && link[0] == '/') link = link.substr(1);
        // Only allow crucial components not locking to answer directly.
        // (We want deadlockdetector status page to be available during a
        // deadlock
    if (link == "" || link == "deadlockdetector") {
        std::ostringstream ost;
        _status.handlePage(urlpath, ost);
        conn.Output(ost.str().c_str());
    } else {
            // Route other status requests that can possibly deadlock to a
            // worker thread.
        vespalib::MonitorGuard monitor(_status._workerMonitor);
        _status._queuedRequests.emplace_back(std::make_shared<HttpRequest>(url.c_str(), urlpath.getServerSpec()));
        HttpRequest* req = _status._queuedRequests.back().get();
        framework::SecondTime timeout(urlpath.get("timeout", 30u));
        framework::SecondTime timeoutTime(_status._component->getClock().getTimeInSeconds() + timeout);
        monitor.signal();
        while (true) {
            monitor.wait(100);
            bool done = false;
            if (req->_result.get()) {
                conn.Output(req->_result->c_str());
                LOG(debug, "Finished status request for '%s'", req->_url.c_str());
                done = true;
            } else {
                if (_status._component->getClock().getTimeInSeconds() > timeoutTime)
                {
                    std::ostringstream ost;
                    {
                        HttpErrorWriter writer(ost, "500 Internal Server Error");
                        writer << "Request " << url.c_str() << " timed out "
                               << "after " << timeout << " seconds.";
                    }
                    LOG(debug, "HTTP status request failed: %s. %zu requests queued",
                        ost.str().c_str(), _status._queuedRequests.size() - 1);
                    conn.Output(ost.str().c_str());
                    done = true;
                }
            }
            if (done) {
                for (std::list<HttpRequest::SP>::iterator it
                        = _status._queuedRequests.begin();
                     it != _status._queuedRequests.end(); ++it)
                {
                    if (it->get() == req) {
                        _status._queuedRequests.erase(it);
                        break;
                    }
                }
                break;
            }
        }
    }
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

void
StatusWebServer::handlePage(const framework::HttpUrlPath& urlpath, std::ostream& out)
{
    vespalib::string link(urlpath.getPath());
    if (link.size() > 0 && link[0] == '/') link = link.substr(1);

    size_t slashPos = link.find('/');
    if (slashPos != std::string::npos) link = link.substr(0, slashPos);

    bool pageExisted = false;
    if ( ! link.empty()) {
        const framework::StatusReporter *reporter = _reporterMap.getStatusReporter(link);
        if (reporter != nullptr) {
            try {
                pageExisted = reporter->reportHttpHeader(out, urlpath);
                if (pageExisted) {
                    pageExisted = reporter->reportStatus(out, urlpath);
                }
            } catch (std::exception &e) {
                HttpErrorWriter writer(out, "500 Internal Server Error");
                writer << "<pre>" << e.what() << "</pre>";
                pageExisted = true;
            }
            if (pageExisted) {
                LOG(spam, "Status finished request");
                return;
            }
        }
    }
    if (!pageExisted && link.size() > 0) {
        HttpErrorWriter writer(out, "404 Not found");
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
        indexRep.reportHttpHeader(out, urlpath);
        indexRep.reportStatus(out, urlpath);
    }
    LOG(spam, "Status finished request");
}

vespalib::string
StatusWebServer::getServerSpec(const vespalib::string &specFromRequest,
                               const vespalib::string &specFromServer)
{
    if (specFromRequest.empty()) {
        // This is a fallback in case the request spec is not given (HTTP 1.0 header)
        return specFromServer;
    }
    return specFromRequest;
}

void
StatusWebServer::run(framework::ThreadHandle& thread)
{
    while (!thread.interrupted()) {
        HttpRequest::SP request;
        {
            vespalib::MonitorGuard monitor(_workerMonitor);
            for (const HttpRequest::SP & cur : _queuedRequests) {
                if ( ! cur->_result ) {
                    request = cur;
                    break;
                }
            }
            if (!request.get()) {
                monitor.wait(10 * 1000);
                thread.registerTick(framework::WAIT_CYCLE);
                continue;
            }
        }
        framework::HttpUrlPath urlpath(request->_url, request->_serverSpec);
        std::ostringstream ost;
        handlePage(urlpath, ost);
        // If the same request is still in front of the queue
        // (it hasn't timed out), add the result to it.
        vespalib::MonitorGuard monitor(_workerMonitor);
        request->_result.reset(new vespalib::string(ost.str()));
        monitor.signal();
        thread.registerTick(framework::PROCESS_CYCLE);
    }
}

} // storage
