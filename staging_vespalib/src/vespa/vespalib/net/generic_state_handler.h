// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "json_get_handler.h"
#include "state_explorer.h"
#include <vespa/vespalib/stllike/string.h>
#include <vector>
#include <map>

namespace vespalib {

/**
 * An implementation of the json get handler interface that exposes
 * the state represented by the given state explorer as a browsable
 * REST sub-API located below the given root path.
 **/
class GenericStateHandler : public JsonGetHandler
{
private:
    std::vector<vespalib::string> _root;
    const StateExplorer &_state;

public:
    GenericStateHandler(const vespalib::string &root_path, const StateExplorer &state);
    virtual vespalib::string get(const vespalib::string &host,
                                 const vespalib::string &path,
                                 const std::map<vespalib::string,vespalib::string> &params) const override;
};

} // namespace vespalib
