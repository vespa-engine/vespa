// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <map>

namespace vespalib::net { class ConnectionAuthContext; }

namespace vespalib {

struct JsonGetHandler {
    class Response {
        int              _status_code;
        vespalib::string _status_or_payload;

        Response(int status_code, vespalib::string status_or_payload);
    public:
        Response(); // By default, 500 Internal Server Error
        ~Response();
        Response(const Response&);
        Response& operator=(const Response&);
        Response(Response&&) noexcept;
        Response& operator=(Response&&) noexcept;

        [[nodiscard]] int status_code() const noexcept { return _status_code; }
        [[nodiscard]] bool ok() const noexcept { return _status_code == 200; }
        [[nodiscard]] bool failed() const noexcept { return _status_code != 200; }
        [[nodiscard]] vespalib::stringref status_message() const noexcept {
            if (_status_code == 200) {
                return "OK";
            } else {
                return _status_or_payload;
            }
        }
        [[nodiscard]] vespalib::stringref payload() const noexcept {
            if (_status_code == 200) {
                return _status_or_payload;
            } else {
                return {};
            }
        }

        [[nodiscard]] static Response make_ok_with_json(vespalib::string json);
        [[nodiscard]] static Response make_failure(int status_code, vespalib::string status_message);
        [[nodiscard]] static Response make_not_found();
    };

    virtual Response get(const vespalib::string &host,
                         const vespalib::string &path,
                         const std::map<vespalib::string,vespalib::string> &params,
                         const net::ConnectionAuthContext &auth_ctx) const = 0;
    virtual ~JsonGetHandler() = default;
};

} // namespace vespalib
