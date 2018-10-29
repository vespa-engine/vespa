// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "http_request.h"

#include <algorithm>
#include <vector>

namespace vespalib::portal {

namespace {

void strip_cr(vespalib::string &str) {
    if (!str.empty() && str[str.size() - 1] == '\r') {
        str.resize(str.size() - 1);
    }
}

std::vector<vespalib::string> split(vespalib::stringref str, vespalib::stringref sep) {
    vespalib::string token;
    std::vector<vespalib::string> list;
    for (char c: str) {
        if (sep.find(c) == vespalib::stringref::npos) {
            token.push_back(c);
        } else if (!token.empty()) {
            list.push_back(token);
            token.clear();
        }
    }
    if (!token.empty()) {
        list.push_back(token);
    }
    return list;
}

} // namespace vespalib::portal::<unnamed>

void
HttpRequest::set_done()
{
    _done = true;
}

void
HttpRequest::set_error()
{
    _error = true;
}

void
HttpRequest::handle_request_line(const vespalib::string &line)
{
    auto parts = split(line, " ");
    if (parts.size() != 3) {
        return set_error(); // malformed request line
    }
    _method = parts[0];
    _uri = parts[1];
    _version = parts[2];
}

void
HttpRequest::handle_header_line(const vespalib::string &line)
{
    if (line.empty()) {
        return set_done();
    }
    size_t pos = 0;
    size_t end = line.size();
    bool continuation = (line[0] == ' ') || (line[0] == '\t');
    if (!continuation) {
        pos = line.find(":");
        if (pos == vespalib::string::npos) {
            return set_error(); // missing header: value separator
        } else {
            _header_name.assign(line, 0, pos++);
            std::transform(_header_name.begin(), _header_name.end(),
                           _header_name.begin(), ::tolower);
        }
    }
    if (_header_name.empty()) {
        return set_error(); // missing header name
    }
    while ((pos < end) && (isspace(line[pos]))) {
        ++pos; // strip leading whitespace
    }
    while ((pos < end) && (isspace(line[end - 1]))) {
        --end; // strip trailing whitespace
    }
    auto header_insert_result = _headers.insert(std::make_pair(_header_name, vespalib::string()));
    bool header_found = !header_insert_result.second;
    vespalib::string &header_value = header_insert_result.first->second;
    if (header_found) {
        if (continuation) {
            header_value.push_back(' ');
        } else { // duplicate header
            header_value.push_back(',');
        }
    }
    header_value.append(line.data() + pos, end - pos);
}

void
HttpRequest::handle_line(const vespalib::string &line)
{
    if (_first) {
        handle_request_line(line);
        _first = false;
    } else {
        handle_header_line(line);
    }
}

HttpRequest::HttpRequest()
    : _method(),
      _uri(),
      _version(),
      _headers(),
      _host(),
      _empty(),
      _first(true),
      _done(false),
      _error(false),
      _header_name(),
      _line_buffer()
{
}

HttpRequest::~HttpRequest() = default;

size_t
HttpRequest::handle_data(const char *buf, size_t len)
{
    size_t used = 0;
    while (need_more_data() && (used < len)) {
        char c = buf[used++];
        if (c != '\n') {
            _line_buffer.push_back(c);
        } else {
            strip_cr(_line_buffer);
            handle_line(_line_buffer);
            _line_buffer.clear();
        }
    }
    return used;
}

void
HttpRequest::resolve_host(const vespalib::string &my_host)
{
    _host = get_header("host");
    if (_host.empty()) {
        _host = my_host;
    }
}

const vespalib::string &
HttpRequest::get_header(const vespalib::string &name) const
{
    auto pos = _headers.find(name);
    if (pos == _headers.end()) {
        return _empty;
    }
    return pos->second;
}

} // namespace vespalib::portal
