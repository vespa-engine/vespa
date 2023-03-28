// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "json_handler_repo.h"
#include <algorithm>

namespace vespalib {

namespace {

template <typename T>
void remove_seq(T &collection, size_t seq) {
    collection.erase(std::remove_if(collection.begin(), collection.end(),
                                    [seq](const typename T::value_type &item)
                                    { return (item.seq == seq); }),
                     collection.end());
}

} // namespace vespalib::<unnamed>

size_t
JsonHandlerRepo::State::bind(vespalib::stringref path_prefix,
                             const JsonGetHandler &get_handler)
{
    std::lock_guard<std::mutex> guard(lock);
    size_t my_seq = ++seq;
    hooks.emplace_back(my_seq, path_prefix, get_handler);
    std::sort(hooks.begin(), hooks.end());
    return my_seq;
}

size_t
JsonHandlerRepo::State::add_root_resource(vespalib::stringref path)
{
    std::lock_guard<std::mutex> guard(lock);
    size_t my_seq = ++seq;
    root_resources.emplace_back(my_seq, path);
    return my_seq;
}

void
JsonHandlerRepo::State::unbind(size_t my_seq) {
    std::lock_guard<std::mutex> guard(lock);
    remove_seq(hooks, my_seq);
    remove_seq(root_resources, my_seq);
}

//-----------------------------------------------------------------------------

JsonHandlerRepo::JsonHandlerRepo()
    : _state(std::make_shared<State>())
{}
JsonHandlerRepo::~JsonHandlerRepo() = default;
JsonHandlerRepo::Token::UP
JsonHandlerRepo::bind(vespalib::stringref path_prefix,
                      const JsonGetHandler &get_handler)
{
    return std::make_unique<Unbinder>(_state, _state->bind(path_prefix, get_handler));
}

JsonHandlerRepo::Token::UP
JsonHandlerRepo::add_root_resource(vespalib::stringref path)
{
    return std::make_unique<Unbinder>(_state, _state->add_root_resource(path));
}

std::vector<vespalib::string>
JsonHandlerRepo::get_root_resources() const
{
    std::lock_guard<std::mutex> guard(_state->lock);
    std::vector<vespalib::string> result;
    for (const Resource &resource: _state->root_resources) {
        result.push_back(resource.path);
    }
    return result;
}

JsonGetHandler::Response
JsonHandlerRepo::get(const vespalib::string &host,
                     const vespalib::string &path,
                     const std::map<vespalib::string,vespalib::string> &params,
                     const net::ConnectionAuthContext &auth_ctx) const
{
    std::lock_guard<std::mutex> guard(_state->lock);
    for (const auto &hook: _state->hooks) {
        if (path.find(hook.path_prefix) == 0) {
            return hook.handler->get(host, path, params, auth_ctx);
        }
    }
    return Response::make_not_found();
}

} // namespace vespalib
