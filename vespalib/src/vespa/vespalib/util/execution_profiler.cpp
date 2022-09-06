// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "execution_profiler.h"
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <cassert>

namespace vespalib {

namespace {

double as_ms(duration d) {
    return (count_ns(d) / 1000000.0);
}

}

struct ExecutionProfiler::ReportContext {
    const ExecutionProfiler::NameMapper &name_mapper;
    vespalib::hash_map<vespalib::string,vespalib::string> name_cache;
    ReportContext(const ExecutionProfiler::NameMapper &name_mapper_in, size_t num_names)
      : name_mapper(name_mapper_in), name_cache(num_names * 2) {}
    const vespalib::string &resolve_name(const vespalib::string &name) {
        auto pos = name_cache.find(name);
        if (pos == name_cache.end()) {
            pos = name_cache.insert(std::make_pair(name, name_mapper(name))).first;
        }
        return pos->second;
    }
};

duration
ExecutionProfiler::get_children_time(const Edges &edges) const
{
    duration result = duration::zero();
    for (const auto &entry: edges) {
        result += _nodes[entry.second].total_time;
    }
    return result;
}

std::vector<uint32_t>
ExecutionProfiler::get_sorted_children(const Edges &edges) const
{
    std::vector<uint32_t> children;
    for (const auto &entry: edges) {
        children.push_back(entry.second);
    }
    std::sort(children.begin(), children.end(),
              [&](const auto &a, const auto &b) {
                  return (_nodes[a].total_time > _nodes[b].total_time);
              });
    return children;
}

void
ExecutionProfiler::render_node(slime::Cursor &obj, NodeId node, ReportContext &ctx) const
{
    obj.setString("name", ctx.resolve_name(_names[_nodes[node].task]));
    obj.setLong("count", _nodes[node].count);
    obj.setDouble("total_time_ms", as_ms(_nodes[node].total_time));
    if (!_nodes[node].children.empty()) {
        auto children_time = get_children_time(_nodes[node].children);
        obj.setDouble("self_time_ms", as_ms(_nodes[node].total_time - children_time));
        render_children(obj.setArray("children"), _nodes[node].children, ctx);
    }
}

void
ExecutionProfiler::render_children(slime::Cursor &arr, const Edges &edges, ReportContext &ctx) const
{
    auto children = get_sorted_children(edges);
    for (NodeId child: children) {
        render_node(arr.addObject(), child, ctx);
    }
}

void
ExecutionProfiler::track_start(TaskId task)
{
    auto &edges = _state.empty() ? _roots : _nodes[_state.back().node].children;
    auto [pos, was_new] = edges.insert(std::make_pair(task, _nodes.size()));
    NodeId node = pos->second; // extending _nodes might invalidate lookup result
    if (was_new) {
        assert(node == _nodes.size());
        _nodes.emplace_back(task);
    }
    assert(node < _nodes.size());
    _state.emplace_back(node);
}

void
ExecutionProfiler::track_complete()
{
    assert(!_state.empty());
    auto &node = _nodes[_state.back().node];
    auto elapsed = steady_clock::now() - _state.back().start;
    ++node.count;
    node.total_time += elapsed;
    _state.pop_back();
}

ExecutionProfiler::ExecutionProfiler(size_t max_depth)
  : _max_depth(max_depth),
    _names(),
    _name_map(),
    _nodes(),
    _roots(),
    _state(),
    _level(0)
{
}

ExecutionProfiler::~ExecutionProfiler() = default;

ExecutionProfiler::TaskId
ExecutionProfiler::resolve(const vespalib::string &name)
{
    auto [pos, was_new] = _name_map.insert(std::make_pair(name, _names.size()));
    if (was_new) {
        assert(pos->second == _names.size());
        _names.push_back(name);
    }
    assert(pos->second < _names.size());
    return pos->second;
}

void
ExecutionProfiler::report(slime::Cursor &obj, const NameMapper &name_mapper) const
{
    ReportContext ctx(name_mapper, std::min(_names.size(), _nodes.size()));
    obj.setDouble("total_time_ms", as_ms(get_children_time(_roots)));
    if (!_roots.empty()) {
        render_children(obj.setArray("roots"), _roots, ctx);
    }
}

}
