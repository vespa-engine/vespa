// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "json_get_handler.h"

namespace vespalib {

JsonGetHandler::Response::Response(int status_code,
                                   vespalib::string status_or_payload,
                                   vespalib::string content_type_override)
    : _status_code(status_code),
      _status_or_payload(std::move(status_or_payload)),
      _content_type_override(std::move(content_type_override))
{}

JsonGetHandler::Response::Response()
    : _status_code(500),
      _status_or_payload("Internal Server Error"),
      _content_type_override()
{}

JsonGetHandler::Response::~Response() = default;

JsonGetHandler::Response::Response(const Response&) = default;
JsonGetHandler::Response& JsonGetHandler::Response::operator=(const Response&) = default;
JsonGetHandler::Response::Response(Response&&) noexcept = default;
JsonGetHandler::Response& JsonGetHandler::Response::operator=(Response&&) noexcept = default;

JsonGetHandler::Response
JsonGetHandler::Response::make_ok_with_json(vespalib::string json)
{
    return {200, std::move(json), {}};
}

JsonGetHandler::Response
JsonGetHandler::Response::make_ok_with_content_type(vespalib::string payload, vespalib::string content_type)
{
    return {200, std::move(payload), std::move(content_type)};
}

JsonGetHandler::Response
JsonGetHandler::Response::make_failure(int status_code, vespalib::string status_message)
{
    return {status_code, std::move(status_message), {}};
}

JsonGetHandler::Response
JsonGetHandler::Response::make_not_found()
{
    return {404, "Not Found", {}};
}

}
