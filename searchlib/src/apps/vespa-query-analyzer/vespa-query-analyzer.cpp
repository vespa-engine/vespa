// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/data/simple_buffer.h>
#include <vespa/vespalib/data/slime/json_format.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/io/mapped_file_input.h>
#include <vespa/vespalib/util/overload.h>
#include <vespa/vespalib/util/signalhandler.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/searchlib/queryeval/flow.h>
#include <variant>
#include <vector>
#include <map>

using namespace vespalib::slime::convenience;
using vespalib::make_string_short::fmt;
using vespalib::slime::JsonFormat;
using vespalib::slime::ARRAY;
using vespalib::slime::OBJECT;
using vespalib::slime::STRING;
using vespalib::slime::DOUBLE;
using vespalib::slime::BOOL;
using search::queryeval::FlowStats;
using search::queryeval::InFlow;

//-----------------------------------------------------------------------------

using Path = std::vector<std::variant<size_t,vespalib::stringref>>;
using Paths = std::vector<Path>;

template <typename F>
struct Matcher : vespalib::slime::ObjectTraverser {
    Path path;
    Paths result;
    F match;
    ~Matcher();
    Matcher(F match_in) noexcept : path(), result(), match(match_in) {}
    void search(const Inspector &node) {
        if (path.empty() && match(path, node)) {
            result.push_back(path);
        }
        if (node.type() == OBJECT()) {
            node.traverse(*this);
        }
        if (node.type() == ARRAY()) {
            size_t size = node.entries();
            for (size_t i = 0; i < size; ++i) {
                path.emplace_back(i);
                if (match(path, node[i])) {
                    result.push_back(path);
                }
                search(node[i]);
                path.pop_back();
            }
        }
    }
    void field(const Memory &symbol, const Inspector &inspector) final {
        path.emplace_back(symbol.make_stringref());
        if (match(path, inspector)) {
            result.push_back(path);
        }
        search(inspector);
        path.pop_back();
    }
};
template <typename F> Matcher<F>::~Matcher() = default;

std::vector<Path> find_field(const Inspector &root, const vespalib::string &name) {
    auto matcher = Matcher([&](const Path &path, const Inspector &){
                               return ((path.size() > 0) &&
                                       (std::holds_alternative<vespalib::stringref>(path.back())) &&
                                       (std::get<vespalib::stringref>(path.back()) == name));
                           });
    matcher.search(root);
    return matcher.result;
}

std::vector<Path> find_tag(const Inspector &root, const vespalib::string &name) {
    auto matcher = Matcher([&](const Path &path, const Inspector &value){
                               return ((path.size() > 0) &&
                                       (std::holds_alternative<vespalib::stringref>(path.back())) &&
                                       (std::get<vespalib::stringref>(path.back()) == "tag") &&
                                       (value.asString().make_stringref() == name));
                           });
    matcher.search(root);
    return matcher.result;
}

vespalib::string path_to_str(const Path &path) {
    size_t cnt = 0;
    vespalib::string str("[");
    for (const auto &item: path) {
        if (cnt++ > 0) {
            str.append(",");
        }
        std::visit(vespalib::overload{
                [&str](size_t value)noexcept{ str.append(fmt("%zu", value)); },
                [&str](vespalib::stringref value)noexcept{ str.append(value); }}, item);
    }
    str.append("]");
    return str;
}

vespalib::string strip_name(vespalib::stringref name) {
    auto end = name.find("<");
    auto ns = name.rfind("::", end);
    size_t begin = (ns > name.size()) ? 0 : ns + 2;
    return name.substr(begin, end - begin);
}

const Inspector &apply_path(const Inspector &node, const Path &path, size_t max = -1) {
    size_t cnt = 0;
    const Inspector *ptr = &node;
    for (const auto &elem: path) {
        if (cnt++ >= max) {
            return *ptr;
        }
        if (std::holds_alternative<size_t>(elem)) {
            ptr = &((*ptr)[std::get<size_t>(elem)]);
        }
        if (std::holds_alternative<vespalib::stringref>(elem)) {
            auto ref = std::get<vespalib::stringref>(elem);
            ptr = &((*ptr)[Memory(ref.data(), ref.size())]);
        }
    }
    return *ptr;
}

void extract(vespalib::string &value, const Inspector &data) {
    if (data.valid() && data.type() == STRING()) {
        value = data.asString().make_stringref();
    }
}

struct Sample {
    enum class Type { INVALID, INIT, SEEK, UNPACK, TERMWISE };
    Type type = Type::INVALID;
    std::vector<size_t> path;
    double self_time_ms = 0.0;
    double total_time_ms = 0.0;
    size_t count = 0;
    Sample(const Inspector &sample) {
        auto name = sample["name"].asString().make_stringref();
        if (ends_with(name, "/init")) {
            type = Type::INIT;
        }
        if (ends_with(name, "/seek")) {
            type = Type::SEEK;
        }
        if (ends_with(name, "/unpack")) {
            type = Type::UNPACK;
        }
        if (ends_with(name, "/termwise")) {
            type = Type::TERMWISE;
        }
        if (starts_with(name, "/")) {
            size_t child = 0;
            for (size_t pos = 1; pos < name.size(); ++pos) {
                char c = name[pos];
                if (c == '/') {
                    path.push_back(child);
                    child = 0;
                } else {
                    if (c < '0' || c > '9') {
                        break;
                    }
                    child = child * 10 + (c - '0');
                }
            }
        }
        self_time_ms = sample["self_time_ms"].asDouble();
        total_time_ms = sample["total_time_ms"].asDouble();
        count = sample["count"].asLong();
    }
    static vespalib::string type_to_str(Type type) {
        switch(type) {
        case Type::INVALID: return "<invalid>";
        case Type::INIT: return "init";
        case Type::SEEK: return "seek";
        case Type::UNPACK: return "unpack";
        case Type::TERMWISE: return "termwise";
        }
        abort();
    }
    static vespalib::string path_to_str(const std::vector<size_t> &path) {
        vespalib::string result("/");
        for (size_t elem: path) {
            result += fmt("%zu/", elem);
        }
        return result;
    }
    vespalib::string to_string() const {
        return fmt("type: %s, path: %s, count: %zu, total_time_ms: %g\n",
                   type_to_str(type).c_str(), path_to_str(path).c_str(), count, total_time_ms);
    }
};

struct Node {
    vespalib::string  type = "unknown";
    bool              strict = false;
    FlowStats         flow_stats = FlowStats(0.0, 0.0, 0.0);
    InFlow            in_flow = InFlow(0.0);
    size_t            count = 0;
    double            self_time_ms = 0.0;
    double            total_time_ms = 0.0;
    std::vector<Node> children;
    Node(const Inspector &obj) {
        extract(type, obj["[type]"]);
        type = strip_name(type);
        strict = obj["strict"].asBool();
        flow_stats.estimate = obj["relative_estimate"].asDouble();
        flow_stats.cost = obj["cost"].asDouble();
        flow_stats.strict_cost = obj["strict_cost"].asDouble();
        const Inspector &list = obj["children"];
        for (size_t i = 0; true; ++i) {
            const Inspector &child = list[fmt("[%zu]", i)];
            if (child.valid()) {
                children.emplace_back(child);
            } else {
                break;
            }
        }
    }
    ~Node();
    void add_sample(const Sample &sample) {
        Node *node = this;
        for (size_t child: sample.path) {
            if (child < node->children.size()) {
                node = &node->children[child];
            } else {
                fprintf(stderr, "... ignoring bad sample: %s\n", sample.to_string().c_str());
                return;
            }
        }
        node->count += sample.count;
        node->self_time_ms += sample.self_time_ms;
        node->total_time_ms += sample.total_time_ms;
    }
    void dump_line(size_t indent) const {
        fprintf(stderr, "|%10zu ", count);
        fprintf(stderr, "|%11.3f ", total_time_ms);
        fprintf(stderr, "|%10.3f | ", self_time_ms);
        for (size_t i = 0; i < indent; ++i) {
            fprintf(stderr, "  ");
        }
        fprintf(stderr, "%s\n", type.c_str());
        for (const Node &child: children) {
            child.dump_line(indent + 1);
        }
    }
    void dump() const {
        fprintf(stderr, "|     count | total_time | self_time | structure\n");
        fprintf(stderr, "+-----------+------------+-----------+-------------------------------\n");
        dump_line(0);
        fprintf(stderr, "+-----------+------------+-----------+-------------------------------\n");
    }
};
Node::~Node() = default;

void each_sample_list(const Inspector &list, auto f) {
    for (size_t i = 0; i < list.entries(); ++i) {
        f(Sample(list[i]));
        each_sample_list(list[i]["children"], f);
    }
}

void each_sample(const Inspector &prof, auto f) {
    each_sample_list(prof["roots"], f);
}

struct State {
    void analyze(const Inspector &root) {
        auto bp_list = find_field(root, "optimized");
        for (const Path &path: bp_list) {
            const Inspector &node = apply_path(root, path, path.size()-3);
            const Inspector &key_field = node["distribution-key"];
            if (key_field.valid()) {
                int key = key_field.asLong();
                Node data(apply_path(root, path));
                auto prof_list = find_tag(node, "match_profiling");
                double total_ms = 0.0;
                std::map<Sample::Type,double> time_map;
                for (const Path &prof_path: prof_list) {
                    const Inspector &prof = apply_path(node, prof_path, prof_path.size()-1);
                    if (prof["profiler"].asString().make_stringref() == "tree") {
                        total_ms += prof["total_time_ms"].asDouble();
                        each_sample(prof, [&](const Sample &sample) {
                                              if (sample.type == Sample::Type::SEEK) {
                                                  data.add_sample(sample);
                                              }
                                              if (sample.path.empty()) {
                                                  time_map[sample.type] += sample.total_time_ms;
                                              }
                                          });
                    }
                }
                data.dump();
                fprintf(stderr, "distribution key: %d, total_time_ms: %g\n", key, total_ms);
                for (auto [type, time]: time_map) {
                    fprintf(stderr, "sample type %s used %g ms total\n", Sample::type_to_str(type).c_str(), time);
                }
            }
        }
    }
};

//-----------------------------------------------------------------------------

void usage(const char *self) {
    fprintf(stderr, "usage: %s <json query result file>\n", self);
    fprintf(stderr, "  analyze query cost (planning vs profiling)\n");
    fprintf(stderr, "  query result must contain optimized blueprint dump\n");
    fprintf(stderr, "  query result must contain match phase tree profiling\n\n");
}

struct MyApp {
    vespalib::string file_name;
    bool parse_params(int argc, char **argv);
    int main();
};

bool
MyApp::parse_params(int argc, char **argv) {
    if (argc != 2) {
        return false;
    }
    file_name = argv[1];
    return true;
}

int
MyApp::main()
{
    vespalib::MappedFileInput file(file_name);
    if (!file.valid()) {
        fprintf(stderr, "could not read input file: '%s'\n",
                file_name.c_str());
        return 1;
    }
    Slime slime;
    if(JsonFormat::decode(file, slime) == 0) {
        fprintf(stderr, "file contains invalid json: '%s'\n",
                file_name.c_str());
        return 1;        
    }
    State state;
    state.analyze(slime.get());
    return 0;
}

int main(int argc, char **argv) {
    MyApp my_app;
    vespalib::SignalHandler::PIPE.ignore();
    if (!my_app.parse_params(argc, argv)) {
        usage(argv[0]);
        return 1;
    }
    return my_app.main();
}

//-----------------------------------------------------------------------------
