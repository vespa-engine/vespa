// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "externpolicy.h"
#include <boost/tokenizer.hpp>
#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/slobrok/sbmirror.h>
#include <vespa/fnet/transport.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fastos/thread.h>

#include <vespa/log/log.h>
LOG_SETUP(".externpolicy");

using slobrok::api::IMirrorAPI;
using slobrok::api::MirrorAPI;

typedef boost::char_separator<char> Separator;
typedef boost::tokenizer<Separator> Tokenizer;

namespace documentapi {

ExternPolicy::ExternPolicy(const string &param) :
    _lock(),
    _threadPool(std::make_unique<FastOS_ThreadPool>(1024*60)),
    _transport(std::make_unique<FNET_Transport>()),
    _orb(std::make_unique<FRT_Supervisor>(_transport.get())),
    _mirror(),
    _pattern(),
    _session(),
    _error("Not initialized."),
    _offset(0),
    _gen(0),
    _recipients(),
    _started(false)
{
    // Parse connection spec.
    if (param.empty()) {
        _error = "Expected parameter, got empty string.";
        return;
    }
    size_t pos = param.find(';');
    if (pos == string::npos || pos == 0 || pos == param.size() - 1) {
        _error = vespalib::make_string("Expected parameter on the form '<spec>;<pattern>', got '%s'.", param.c_str());
        return;
    }

    // Activate supervisor and register mirror.
    MirrorAPI::StringList spec;
    string lst = param.substr(0, pos);
    std::string stdlst(lst);
    Tokenizer tokens(stdlst, Separator(","));
    for (Tokenizer::iterator it = tokens.begin(); it != tokens.end(); ++it) {
        spec.push_back(*it);
    }

    if (spec.empty()) {
        _error = vespalib::make_string("Extern policy needs at least one slobrok: Slobrok list '%s' resolved to no slobroks", lst.c_str());
        return;
    }

    slobrok::ConfiguratorFactory config(spec);
    _mirror = std::make_unique<MirrorAPI>(*_orb, config);
    _started = _transport->Start(_threadPool.get());
    if (!_started) {
        _error = "Failed to start FNET supervisor.";
        return;
    } else {
        LOG(debug, "Connecting to extern slobrok mirror '%s'..", lst.c_str());
    }

    // Parse query pattern.
    _pattern = param.substr(pos + 1);
    pos = _pattern.find_last_of('/');
    if (pos == string::npos) {
        _error = vespalib::make_string("Expected pattern on the form '<service>/<session>', got '%s'.", _pattern.c_str());
        return;
    }
    _session = _pattern.substr(pos);

    // All ok.
    _error.clear();
}

ExternPolicy::~ExternPolicy()
{
    _mirror.reset();
    if (_started) {
        _transport->ShutDown(true);
    }
}

void
ExternPolicy::select(mbus::RoutingContext &ctx)
{
    if (!_error.empty()) {
        ctx.setError(DocumentProtocol::ERROR_POLICY_FAILURE, _error);
    } else if (_mirror->ready()) {
        mbus::Hop hop = getRecipient();
        if (hop.hasDirectives()) {
            mbus::Route route = ctx.getRoute();
            route.setHop(0, hop);
            ctx.addChild(route);
        } else {
            ctx.setError(mbus::ErrorCode::NO_ADDRESS_FOR_SERVICE,
                         vespalib::make_string("Could not resolve any recipients from '%s'.", _pattern.c_str()));
        }
    } else {
        ctx.setError(mbus::ErrorCode::APP_TRANSIENT_ERROR, "Extern slobrok not ready.");
    }

}

void
ExternPolicy::merge(mbus::RoutingContext &ctx)
{
    DocumentProtocol::merge(ctx);
}

mbus::Hop
ExternPolicy::getRecipient()
{
    std::lock_guard guard(_lock);
    update();
    if (_recipients.empty()) {
        return mbus::Hop();
    }
    return _recipients[++_offset % _recipients.size()];
}

void
ExternPolicy::update()
{
    uint32_t upd = _mirror->updates();
    if (_gen != upd) {
        _gen = upd;
        _recipients.clear();

        IMirrorAPI::SpecList entries = _mirror->lookup(_pattern);
        if (!entries.empty()) {
            for (const auto & spec : entries)
            {
                _recipients.push_back(mbus::Hop::parse(spec.second + _session));
            }
        }
    }
}

}
