// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "translogserverapp.h"
#include <vespa/config/subscription/configuri.h>

#include <vespa/log/log.h>
LOG_SETUP(".translogserverapp");

using search::common::FileHeaderContext;

namespace search::transactionlog {

TransLogServerApp::TransLogServerApp(const config::ConfigUri & tlsConfigUri,
                                     const FileHeaderContext & fileHeaderContext)
    : _lock(),
      _tls(),
      _tlsConfig(),
      _tlsConfigFetcher(tlsConfigUri.getContext()),
      _fileHeaderContext(fileHeaderContext)
{
    _tlsConfigFetcher.subscribe<searchlib::TranslogserverConfig>(tlsConfigUri.getConfigId(), this);
    _tlsConfigFetcher.start();
}

namespace {

DomainPart::Crc
getCrc(searchlib::TranslogserverConfig::Crcmethod crcType)
{
    switch (crcType) {
        case searchlib::TranslogserverConfig::ccitt_crc32:
            return DomainPart::ccitt_crc32;
        case searchlib::TranslogserverConfig::xxh64:
            return DomainPart::xxh64;
    }
    abort();
}

}

void
TransLogServerApp::start()
{
    std::shared_ptr<searchlib::TranslogserverConfig> c = _tlsConfig.get();
    auto tls = std::make_shared<TransLogServer>(c->servername, c->listenport, c->basedir, _fileHeaderContext,
                                            c->filesizemax, c->maxthreads, getCrc(c->crcmethod));
    std::lock_guard<std::mutex> guard(_lock);
    _tls = std::move(tls);
}

TransLogServerApp::~TransLogServerApp()
{
    _tlsConfigFetcher.close();
}

void
TransLogServerApp::configure(std::unique_ptr<searchlib::TranslogserverConfig> cfg)
{
    LOG(config, "configure Transaction Log Server %s at port %d", cfg->servername.c_str(), cfg->listenport);
    _tlsConfig.set(cfg.release());
    _tlsConfig.latch();
}

TransLogServer::SP
TransLogServerApp::getTransLogServer() const {
    std::lock_guard<std::mutex> guard(_lock);
    return _tls;
}

}
