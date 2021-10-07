// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "request.h"
#include <cassert>
#include <algorithm>

namespace vespalib::ws {

namespace {

void split(vespalib::stringref str, vespalib::stringref sep,
           std::vector<vespalib::string> &dst)
{
    dst.clear();
    vespalib::string tmp;
    for (size_t i = 0; i < str.size(); ++i) {
        if (sep.find(str[i]) != vespalib::string::npos) {
            if (!tmp.empty()) {
                dst.push_back(tmp);
                tmp.clear();
            }
        } else {
            tmp.push_back(str[i]);
        }
    }
    if (!tmp.empty()) {
        dst.push_back(tmp);
    }
}

} // namespace vespalib::ws::<unnamed>

Request::Request() { }
Request::~Request() { }

bool
Request::handle_header(vespalib::string &header_name,
                       const vespalib::string &header_line)
{
    assert(!header_line.empty());
    size_t pos = 0;
    size_t end = header_line.size();
    bool continuation = (header_line[0] == ' ') || (header_line[0] == '\t');
    if (!continuation) {
        pos = header_line.find(":");
        if (pos == vespalib::string::npos) {
            return false;
        } else {
            header_name.assign(header_line, 0, pos++);
            std::transform(header_name.begin(), header_name.end(),
                           header_name.begin(), ::tolower);
        }
    }
    while ((pos < end) && (isspace(header_line[pos]))) {
        ++pos; // strip leading whitespace
    }
    while ((pos < end) && (isspace(header_line[end - 1]))) {
        --end; // strip trailing whitespace
    }
    if (header_name.empty()) {
        return false;
    }
    auto header_insert_result = _headers.insert(std::make_pair(header_name, vespalib::string()));
    bool header_found = !header_insert_result.second;
    vespalib::string &header_value = header_insert_result.first->second;
    if (!header_found) {
        header_value.assign(header_line, pos, end - pos);
    } else {
        if (continuation) {
            header_value.push_back(' ');
        } else { // duplicate header
            header_value.push_back(',');
        }
        header_value.append(header_line.data() + pos, end - pos);
    }
    return true;
}

bool
Request::read_header(Connection &conn)
{
    vespalib::string line;
    vespalib::string header_name_space;
    std::vector<vespalib::string> parts;
    if (!conn.read_line(line)) {
        return false;
    }
    split(line, "\t ", parts);
    if (parts.size() != 3) {
        return false;
    }
    _method = parts[0];
    _uri = parts[1];
    _version = parts[2];
    while (conn.read_line(line)) {
        if (line.empty()) {
            fprintf(stderr, "request: %s %s %s\n",
                    _method.c_str(), _uri.c_str(), _version.c_str());
            for (const auto &h: _headers) {
                fprintf(stderr, "request: '%s' -> '%s'\n",
                        h.first.c_str(), h.second.c_str());
            }
            return true; // done
        }
        if (!handle_header(header_name_space, line)) {
            return false; // malformed header
        }
    }
    return false; // incomplete headers
}

const vespalib::string &
Request::get_header(const vespalib::string &name) const
{
    auto pos = _headers.find(name);
    if (pos == _headers.end()) {
        return _empty;
    }
    return pos->second;
}

bool
Request::has_connection_token(const vespalib::string &token) const
{
    std::vector<vespalib::string> tokens;
    split(get_header("connection"), ",\t ", tokens);
    for (const auto &t: tokens) {
        if (strcasecmp(t.c_str(), token.c_str()) == 0) {
            return true;
        }
    }
    return false;
}

bool
Request::is_ws_upgrade() const
{
    return ((strcasecmp(get_header("upgrade").c_str(), "websocket") == 0) &&
            has_connection_token("upgrade"));
}

}
