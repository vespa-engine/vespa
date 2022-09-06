// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "time.h"

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <functional>

namespace vespalib {

namespace slime { struct Cursor; }

/**
 * A simple single-threaded profiler used to measure where time is
 * spent when executing tasks that may depend on each other (doing one
 * task includes doing another task; like one function calls another
 * function). Each task is identified by a unique name. Data is
 * collected in real-time using signals about when a task is started
 * and when it completes. Any sub-task must complete before any parent
 * task. Any task may be executed any number of times and may depend
 * on any other task.
 **/
class ExecutionProfiler {
public:
    using TaskId = uint32_t;
    using NameMapper = std::function<vespalib::string(const vespalib::string &)>;
private:
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
    struct ReportContext;

    size_t _max_depth;
    std::vector<vespalib::string> _names;
    vespalib::hash_map<vespalib::string,size_t> _name_map;
    std::vector<Node> _nodes;
    Edges _roots;
    std::vector<Frame> _state;
    size_t _level;

    duration get_children_time(const Edges &edges) const;
    std::vector<NodeId> get_sorted_children(const Edges &edges) const;
    void render_node(slime::Cursor &obj, NodeId node, ReportContext &ctx) const;
    void render_children(slime::Cursor &arr, const Edges &edges, ReportContext &ctx) const;

    void track_start(TaskId task);
    void track_complete();

public:
    ExecutionProfiler(size_t max_depth);
    ~ExecutionProfiler();
    TaskId resolve(const vespalib::string &name);
    void start(TaskId task) {
        if (++_level <= _max_depth) {
            track_start(task);
        }
    }
    void complete() {
        if (--_level < _max_depth) {
            track_complete();
        }
    }
    void report(slime::Cursor &obj, const NameMapper & = [](const vespalib::string &name) noexcept { return name; }) const;
};

}
