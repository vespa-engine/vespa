// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "json_get_handler.h"
#include "state_explorer.h"
#include <map>
#include <string>
#include <vector>

namespace vespalib {

/**
 * An implementation of the json get handler interface that exposes
 * the state represented by the given state explorer as a browsable
 * REST sub-API located below the given root path.
 **/
class GenericStateHandler : public JsonGetHandler
{
private:
    std::vector<std::string> _root;
    const StateExplorer &_state;

public:
    GenericStateHandler(const std::string &root_path, const StateExplorer &state);
    Response get(const std::string &host,
                 const std::string &path,
                 const std::map<std::string,std::string> &params,
                 const net::ConnectionAuthContext &auth_ctx) const override;
};

} // namespace vespalib
