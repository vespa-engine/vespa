// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "vbench.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/net/tls/tls_crypto_engine.h>

using vespalib::CryptoEngine;

namespace vbench {

namespace {

using IllArg = vespalib::IllegalArgumentException;

string maybe_load(const vespalib::slime::Inspector &file_ref) {
    if (file_ref.valid()) {
        string file_name = file_ref.asString().make_string();
        vespalib::MappedFileInput file(file_name);
        if (file.valid()) {
            return string(file.get().data, file.get().size);
        } else {
            throw IllArg(strfmt("could not load file: '%s'", file_name.c_str()));
        }
    }
    return "";
}

CryptoEngine::SP setup_crypto(const vespalib::slime::Inspector &tls) {
    if (!tls.valid()) {
        return std::make_shared<vespalib::NullCryptoEngine>();
    }
    auto ts_builder = vespalib::net::tls::TransportSecurityOptions::Params().
            ca_certs_pem(maybe_load(tls["ca-certificates"])).
            cert_chain_pem(maybe_load(tls["certificates"])).
            private_key_pem(maybe_load(tls["private-key"])).
            authorized_peers(vespalib::net::tls::AuthorizedPeers::allow_all_authenticated()).
            disable_hostname_validation(true); // TODO configurable or default false!
    return std::make_shared<vespalib::TlsCryptoEngine>(vespalib::net::tls::TransportSecurityOptions(std::move(ts_builder)));
}

} // namespace vbench::<unnamed>

VESPA_THREAD_STACK_TAG(vbench_inputchain_generator);

VBench::VBench(const vespalib::Slime &cfg)
    : _factory(),
      _analyzers(),
      _scheduler(),
      _inputs(),
      _taint()
{
    CryptoEngine::SP crypto = setup_crypto(cfg.get()["tls"]);
    _analyzers.push_back(Analyzer::UP(new RequestSink()));
    vespalib::slime::Inspector &analyzers = cfg.get()["analyze"];
    for (size_t i = analyzers.children(); i-- > 0; ) {
        Analyzer::UP obj = _factory.createAnalyzer(analyzers[i], *_analyzers.back());
        if (obj.get() != 0) {
            _analyzers.push_back(Analyzer::UP(obj.release()));
        }
    }
    _scheduler.reset(new RequestScheduler(crypto,
                                          *_analyzers.back(),
                                          cfg.get()["http_threads"].asLong()));
    vespalib::slime::Inspector &inputs = cfg.get()["inputs"];
    for (size_t i = inputs.children(); i-- > 0; ) {
        vespalib::slime::Inspector &input = inputs[i];
        vespalib::slime::Inspector &taggers = input["prepare"];
        vespalib::slime::Inspector &generator = input["source"];
        InputChain::UP inputChain(new InputChain());
        for (size_t j = taggers.children(); j-- > 0; ) {
            Handler<Request> &next = (j == (taggers.children() - 1))
                                     ? ((Handler<Request>&)*_scheduler)
                                     : ((Handler<Request>&)*inputChain->taggers.back());
            Tagger::UP obj = _factory.createTagger(taggers[j], next);
            if (obj.get() != 0) {
                inputChain->taggers.push_back(Tagger::UP(obj.release()));
            }
        }
        inputChain->generator = _factory.createGenerator(generator, *inputChain->taggers.back());
        if (inputChain->generator.get() != 0) {
            _inputs.push_back(std::move(inputChain));
        }
    }
}

VBench::~VBench() {}

void
VBench::abort()
{
    fprintf(stderr, "aborting...\n");
    for (size_t i = 0; i < _inputs.size(); ++i) {
        _inputs[i]->generator->abort();
    }
    _scheduler->abort();
}

void
VBench::run()
{
    _scheduler->start();
    for (size_t i = 0; i < _inputs.size(); ++i) {
        _inputs[i]->thread = vespalib::thread::start(*_inputs[i]->generator, vbench_inputchain_generator);
    }
    for (size_t i = 0; i < _inputs.size(); ++i) {
        _inputs[i]->thread.join();
    }
    _scheduler->stop().join();
    for (size_t i = 0; i < _inputs.size(); ++i) {
        if (_inputs[i]->generator->tainted()) {
            _taint = _inputs[i]->generator->tainted();
        }
    }
    for (size_t i = 0; i < _analyzers.size(); ++i) {
        _analyzers[i]->report();
    }
}

} // namespace vbench
