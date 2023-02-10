// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "execution_profiler.h"
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/data/slime/slime.h>
#include <cassert>

namespace vespalib {

struct ExecutionProfiler::ReportContext {
    const ExecutionProfiler &profiler;
    const ExecutionProfiler::NameMapper &name_mapper;
    vespalib::hash_map<TaskId,vespalib::string> name_cache;
    ReportContext(const ExecutionProfiler &profiler_in,
                  const ExecutionProfiler::NameMapper &name_mapper_in, size_t num_names)
      : profiler(profiler_in), name_mapper(name_mapper_in), name_cache(num_names * 2) {}
    size_t get_max_depth() const { return profiler._max_depth; }
    const vespalib::string &resolve_name(TaskId task) {
        auto pos = name_cache.find(task);
        if (pos == name_cache.end()) {
            pos = name_cache.insert(std::make_pair(task, name_mapper(profiler.name_of(task)))).first;
        }
        return pos->second;
    }
};

namespace {

double as_ms(duration d) {
    return (count_ns(d) / 1000000.0);
}

class TreeProfiler : public ExecutionProfiler::Impl
{
private:
    using ReportContext = ExecutionProfiler::ReportContext;
    using TaskId = ExecutionProfiler::TaskId;
    using NodeId = uint32_t;
    using Edges = vespalib::hash_map<TaskId, NodeId>;
    struct Node {
        TaskId task;
        size_t count;
        duration total_time;
        Edges children;
        Node(TaskId task_in) noexcept
          : task(task_in),
            count(0),
            total_time(),
            children() {}
    };
    struct Frame {
        NodeId node;
        steady_time start;
        Frame(NodeId node_in) noexcept
          : node(node_in), start(steady_clock::now()) {}
    };

    std::vector<Node> _nodes;
    Edges _roots;
    std::vector<Frame> _state;

    duration get_children_time(const Edges &edges) const {
        duration result = duration::zero();
        for (const auto &entry: edges) {
            result += _nodes[entry.second].total_time;
        }
        return result;
    }
    std::vector<NodeId> get_sorted_children(const Edges &edges) const {
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
    void render_node(slime::Cursor &obj, NodeId node, ReportContext &ctx) const {
        obj.setString("name", ctx.resolve_name(_nodes[node].task));
        obj.setLong("count", _nodes[node].count);
        obj.setDouble("total_time_ms", as_ms(_nodes[node].total_time));
        if (!_nodes[node].children.empty()) {
            auto children_time = get_children_time(_nodes[node].children);
            obj.setDouble("self_time_ms", as_ms(_nodes[node].total_time - children_time));
            render_children(obj.setArray("children"), _nodes[node].children, ctx);
        }
    }
    void render_children(slime::Cursor &arr, const Edges &edges, ReportContext &ctx) const {
        auto children = get_sorted_children(edges);
        for (NodeId child: children) {
            render_node(arr.addObject(), child, ctx);
        }
    }
public:
    TreeProfiler() : _nodes(), _roots(), _state() {}
    void track_start(TaskId task) override {
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
    void track_complete() override {
        assert(!_state.empty());
        auto &node = _nodes[_state.back().node];
        auto elapsed = steady_clock::now() - _state.back().start;
        ++node.count;
        node.total_time += elapsed;
        _state.pop_back();
    }
    void report(slime::Cursor &obj, ReportContext &ctx) const override {
        obj.setString("profiler", "tree");
        obj.setLong("depth", ctx.get_max_depth());
        obj.setDouble("total_time_ms", as_ms(get_children_time(_roots)));
        if (!_roots.empty()) {
            render_children(obj.setArray("roots"), _roots, ctx);
        }
    }
};

class FlatProfiler : public ExecutionProfiler::Impl
{
private:
    using ReportContext = ExecutionProfiler::ReportContext;
    using TaskId = ExecutionProfiler::TaskId;
    struct Node {
        size_t count;
        duration self_time;
        Node() noexcept
          : count(0),
            self_time() {}
    };
    struct Frame {
        TaskId task;
        steady_time start;
        duration overlap;
        Frame(TaskId task_in) noexcept
          : task(task_in), start(steady_clock::now()), overlap() {}
    };

    size_t _topn;
    std::vector<Node> _nodes;
    std::vector<Frame> _state;

    duration get_total_time() const {
        duration result = duration::zero();
        for (const auto &node: _nodes) {
            result += node.self_time;
        }
        return result;
    }
    std::vector<uint32_t> get_sorted_nodes() const {
        std::vector<uint32_t> nodes;
        nodes.reserve(_nodes.size());
        for (uint32_t i = 0; i < _nodes.size(); ++i) {
            if (_nodes[i].count > 0) {
                nodes.push_back(i);
            }
        }
        std::sort(nodes.begin(), nodes.end(),
                  [&](const auto &a, const auto &b) {
                      return (_nodes[a].self_time > _nodes[b].self_time);
                  });
        return nodes;
    }
    void render_node(slime::Cursor &obj, uint32_t node, ReportContext &ctx) const {
        obj.setString("name", ctx.resolve_name(node));
        obj.setLong("count", _nodes[node].count);
        obj.setDouble("self_time_ms", as_ms(_nodes[node].self_time));
    }
public:
    FlatProfiler(size_t topn) : _topn(topn), _nodes(), _state() {
        _nodes.reserve(256);
        _state.reserve(64);
    }
    void track_start(TaskId task) override {
        if (task >= _nodes.size()) {
            _nodes.resize(task + 1);
        }
        _state.emplace_back(task);
    }
    void track_complete() override {
        assert(!_state.empty());
        auto &state = _state.back();
        auto &node = _nodes[state.task];
        auto elapsed = steady_clock::now() - state.start;
        ++node.count;
        node.self_time += (elapsed - state.overlap);
        _state.pop_back();
        if (!_state.empty()) {
            _state.back().overlap += elapsed;
        }
    }
    void report(slime::Cursor &obj, ReportContext &ctx) const override {
        obj.setString("profiler", "flat");
        obj.setLong("topn", _topn);
        obj.setDouble("total_time_ms", as_ms(get_total_time()));
        auto list = get_sorted_nodes();
        if (auto limit = std::min(list.size(), _topn); limit > 0) {
            auto &arr = obj.setArray("roots");
            for (uint32_t i = 0; i < limit; ++i) {
                render_node(arr.addObject(), list[i], ctx);
            }
        }
    }
};

}

ExecutionProfiler::ExecutionProfiler(int32_t profile_depth)
  : _level(0),
    _max_depth(),
    _names(),
    _name_map(),
    _impl()
{
    if (profile_depth < 0) {
        _max_depth = -1;
        size_t topn = -profile_depth;
        _impl = std::make_unique<FlatProfiler>(topn);
    } else {
        _max_depth = profile_depth;
        _impl = std::make_unique<TreeProfiler>();
    }
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
    ReportContext ctx(*this, name_mapper, _names.size());
    _impl->report(obj, ctx);
}

}
