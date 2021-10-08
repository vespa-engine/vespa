// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#pragma once

#include "connection.h"
#include <vespa/vespalib/stllike/string.h>
#include <map>
#include <vector>

namespace vespalib {
namespace ws {

class Request
{
private:
    vespalib::string _method;
    vespalib::string _uri;
    vespalib::string _version;
    std::map<vespalib::string, vespalib::string> _headers;
    vespalib::string _empty;

    bool handle_header(vespalib::string &header_name,
                       const vespalib::string &header_line);

public:
    Request();
    ~Request();
    bool read_header(Connection &conn);
    bool is_get() const { return _method == "GET"; }
    const vespalib::string &get_header(const vespalib::string &name) const;
    bool has_connection_token(const vespalib::string &token) const;
    bool is_ws_upgrade() const;
    const vespalib::string &uri() const { return _uri; }
};

} // namespace vespalib::ws
} // namespace vespalib
