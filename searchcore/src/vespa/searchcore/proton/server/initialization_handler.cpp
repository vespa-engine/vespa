// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "initialization_handler.h"

#include <vespa/vespalib/net/tls/capability.h>
#include <vespa/vespalib/util/jsonwriter.h>
#include <functional>

using vespalib::JsonGetHandler;
using vespalib::net::tls::Capability;
using vespalib::net::tls::CapabilitySet;

namespace proton {

namespace {

JsonGetHandler::Response cap_checked(const vespalib::net::ConnectionAuthContext &auth_ctx,
                                               CapabilitySet required_caps,
                                               std::function<std::string()> fn)
{
    if (!auth_ctx.capabilities().contains_all(required_caps)) {
        return JsonGetHandler::Response::make_failure(403, "Forbidden");
    }
    return JsonGetHandler::Response::make_ok_with_json(fn());
}

std::string respond_initialization() {
    vespalib::JSONStringer json;
    json.beginObject();

    json.appendKey("Hello");
    json.appendString("World");

    json.endObject();
    return json.str();
}

} // namespace proton::unnamed

InitializationHandler::InitializationHandler()
{
}

JsonGetHandler::Response
InitializationHandler::get(const std::string &/*host*/,
              const std::string &path,
              const std::map<std::string,std::string> &/*params*/,
              const vespalib::net::ConnectionAuthContext &auth_ctx) const
{
    if (path == "/state/v1/initialization") {
        return cap_checked(auth_ctx, CapabilitySet::make_empty(), [&] {
            return respond_initialization();
        });
    } else {
        // TODO Return different error (?)
        return Response::make_failure(403, "Forbidden");
    }
}

} // namespace proton
