// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "json_get_handler.h"

namespace vespalib {

JsonGetHandler::Response::Response(int status_code, vespalib::string status_or_payload)
    : _status_code(status_code),
      _status_or_payload(std::move(status_or_payload))
{}

JsonGetHandler::Response::Response()
    : _status_code(500),
      _status_or_payload("Internal Server Error")
{}

JsonGetHandler::Response::~Response() = default;

JsonGetHandler::Response::Response(const Response&) = default;
JsonGetHandler::Response& JsonGetHandler::Response::operator=(const Response&) = default;
JsonGetHandler::Response::Response(Response&&) noexcept = default;
JsonGetHandler::Response& JsonGetHandler::Response::operator=(Response&&) noexcept = default;

JsonGetHandler::Response
JsonGetHandler::Response::make_ok_with_json(vespalib::string json)
{
    return {200, std::move(json)};
}

JsonGetHandler::Response
JsonGetHandler::Response::make_failure(int status_code, vespalib::string status_message)
{
    return {status_code, std::move(status_message)};
}

JsonGetHandler::Response
JsonGetHandler::Response::make_not_found()
{
    return {404, "Not Found"};
}

}
