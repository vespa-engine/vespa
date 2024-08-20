// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <map>
#include <string>

namespace vespalib::portal {

class HttpRequest
{
private:
    // http stuff
    std::string _method;
    std::string _uri;
    std::string _path;
    std::map<std::string, std::string> _params;
    std::string _version;
    std::map<std::string, std::string> _headers;
    std::string _host;
    // internal state
    std::string _empty;
    bool             _first;
    bool             _done;
    bool             _error;
    std::string _header_name;
    std::string _line_buffer;

    void set_done();
    void set_error();

    void handle_request_line(const std::string &line);
    void handle_header_line(const std::string &line);
    void handle_line(const std::string &line);

public:
    HttpRequest();
    ~HttpRequest();
    size_t handle_data(const char *buf, size_t len);
    bool need_more_data() const { return (!_error && !_done); }
    bool valid() const { return (!_error && _done); }
    bool is_get() const { return _method == "GET"; }
    void resolve_host(const std::string &my_host);
    const std::string &get_header(const std::string &name) const;
    const std::string &get_host() const { return _host; }
    const std::string &get_uri() const { return _uri; }
    const std::string &get_path() const { return _path; }
    bool has_param(const std::string &name) const;
    const std::string &get_param(const std::string &name) const;
    std::map<std::string, std::string> export_params() const { return _params; }
};

} // namespace vespalib::portal
