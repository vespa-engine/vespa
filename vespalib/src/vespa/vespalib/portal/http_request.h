// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

#include <map>

namespace vespalib::portal {

class HttpRequest
{
private:
    // http stuff
    vespalib::string _method;
    vespalib::string _uri;
    vespalib::string _path;
    std::map<vespalib::string, vespalib::string> _params;
    vespalib::string _version;
    std::map<vespalib::string, vespalib::string> _headers;
    vespalib::string _host;
    // internal state
    vespalib::string _empty;
    bool             _first;
    bool             _done;
    bool             _error;
    vespalib::string _header_name;
    vespalib::string _line_buffer;

    void set_done();
    void set_error();

    void handle_request_line(const vespalib::string &line);
    void handle_header_line(const vespalib::string &line);
    void handle_line(const vespalib::string &line);

public:
    HttpRequest();
    ~HttpRequest();
    size_t handle_data(const char *buf, size_t len);
    bool need_more_data() const { return (!_error && !_done); }
    bool valid() const { return (!_error && _done); }
    bool is_get() const { return _method == "GET"; }
    void resolve_host(const vespalib::string &my_host);
    const vespalib::string &get_header(const vespalib::string &name) const;
    const vespalib::string &get_host() const { return _host; }
    const vespalib::string &get_uri() const { return _uri; }
    const vespalib::string &get_path() const { return _path; }
    bool has_param(const vespalib::string &name) const;
    const vespalib::string &get_param(const vespalib::string &name) const;
    std::map<vespalib::string, vespalib::string> export_params() const { return _params; }
};

} // namespace vespalib::portal
