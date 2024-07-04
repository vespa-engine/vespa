// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/data/simple_buffer.h>
#include <vespa/vespalib/data/slime/json_format.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/io/mapped_file_input.h>
#include <vespa/vespalib/util/overload.h>
#include <vespa/vespalib/util/signalhandler.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/searchlib/queryeval/flow.h>
#include <vespa/searchlib/queryeval/flow_tuning.h>
#include <optional>
#include <variant>
#include <vector>
#include <map>
#include <unistd.h>

using namespace vespalib::slime::convenience;
using vespalib::make_string_short::fmt;
using namespace search::queryeval;
using namespace vespalib::slime;
using namespace vespalib;

//-----------------------------------------------------------------------------

int rel_diff(double a, double b, double e, double m) {
    int res = 0;
    if (a < e && b < e) {
        return res;
    }
    double x = std::abs(b - a) / std::max(std::min(a, b), e);
    while (x > m && res < 10) {
        x /= 10.0;
        ++res;
    }
    return res;
}

void apply_diff(vespalib::string &str, int diff, char small, char big, int len) {
    for (int i = 0; i < diff && i < len; ++i) {
        if (diff + i >= len * 2) {
            str.append(big);
        } else {
            str.append(small);
        }
    }
}

using Path = std::vector<std::variant<size_t,std::string_view>>;
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
        path.emplace_back(symbol.make_stringview());
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
                                       (std::holds_alternative<std::string_view>(path.back())) &&
                                       (std::get<std::string_view>(path.back()) == name));
                           });
    matcher.search(root);
    return matcher.result;
}

std::vector<Path> find_tag(const Inspector &root, const vespalib::string &name) {
    auto matcher = Matcher([&](const Path &path, const Inspector &value){
                               return ((path.size() > 0) &&
                                       (std::holds_alternative<std::string_view>(path.back())) &&
                                       (std::get<std::string_view>(path.back()) == "tag") &&
                                       (value.asString().make_stringview() == name));
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
                [&str](std::string_view value)noexcept{ str.append(value); }}, item);
    }
    str.append("]");
    return str;
}

vespalib::string strip_name(std::string_view name) {
    auto end = name.find("<");
    auto ns = name.rfind("::", end);
    size_t begin = (ns > name.size()) ? 0 : ns + 2;
    return vespalib::string(name.substr(begin, end - begin));
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
        if (std::holds_alternative<std::string_view>(elem)) {
            auto ref = std::get<std::string_view>(elem);
            ptr = &((*ptr)[Memory(ref.data(), ref.size())]);
        }
    }
    return *ptr;
}

void extract(vespalib::string &value, const Inspector &data) {
    if (data.valid() && data.type() == STRING()) {
        value = data.asString().make_stringview();
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
        auto name = sample["name"].asString().make_stringview();
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
        count = sample["count"].asLong();
        total_time_ms = sample["total_time_ms"].asDouble();
        const Inspector &self = sample["self_time_ms"];
        if (self.valid()) {
            self_time_ms = self.asDouble();
        } else {
            // Self time is not reported for leaf nodes. Make sure
            // profile depth is high enough to not clip the tree
            // before reaching actual leafs.
            self_time_ms = total_time_ms;
        }
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

struct BlueprintMeta {
    static AnyFlow no_flow(InFlow) { abort(); }
    static double no_self_cost(double, size_t) { return 0.0; }
    struct MetaEntry {
        std::function<AnyFlow(InFlow)> make_flow;
        std::function<double(double, size_t)> self_cost_strict;
        std::function<double(double, size_t)> self_cost_non_strict;
        MetaEntry()
          : make_flow(no_flow),
            self_cost_strict(no_self_cost),
            self_cost_non_strict(no_self_cost) {}
        MetaEntry(std::function<AnyFlow(InFlow)> make_flow_in)
          : make_flow(make_flow_in),
            self_cost_strict(no_self_cost),
            self_cost_non_strict(no_self_cost) {}
        MetaEntry(std::function<AnyFlow(InFlow)> make_flow_in,
                  std::function<double(double, size_t)> self_cost_strict_in)
          : make_flow(make_flow_in),
            self_cost_strict(self_cost_strict_in),
            self_cost_non_strict(no_self_cost) {}
        MetaEntry(std::function<AnyFlow(InFlow)> make_flow_in,
                  std::function<double(double, size_t)> self_cost_strict_in,
                  std::function<double(double, size_t)> self_cost_non_strict_in)
          : make_flow(make_flow_in),
            self_cost_strict(self_cost_strict_in),
            self_cost_non_strict(self_cost_non_strict_in) {}
        ~MetaEntry();
    };
    std::map<vespalib::string,MetaEntry> map;
    BlueprintMeta() {
        map["AndNotBlueprint"] = MetaEntry{
            [](InFlow in_flow)noexcept{ return AnyFlow::create<AndNotFlow>(in_flow); }
        };
        map["AndBlueprint"] = MetaEntry{
            [](InFlow in_flow)noexcept{ return AnyFlow::create<AndFlow>(in_flow); }
        };
        map["OrBlueprint"] = MetaEntry{
            [](InFlow in_flow)noexcept{ return AnyFlow::create<OrFlow>(in_flow); },
            [](double est, size_t n)noexcept{ return flow::heap_cost(est, n); }
        };
        map["WeakAndBlueprint"] = MetaEntry{
            [](InFlow in_flow)noexcept{ return AnyFlow::create<OrFlow>(in_flow); },
            [](double est, size_t n)noexcept{ return flow::heap_cost(est, n); }
        };
        map["NearBlueprint"] = MetaEntry{
            [](InFlow in_flow)noexcept{ return AnyFlow::create<AndFlow>(in_flow); },
            [](double est, size_t n)noexcept{ return est * n; },
            [](double est, size_t n)noexcept{ return est * n; }
        };
        map["ONearBlueprint"] = MetaEntry{
            [](InFlow in_flow)noexcept{ return AnyFlow::create<AndFlow>(in_flow); },
            [](double est, size_t n)noexcept{ return est * n; },
            [](double est, size_t n)noexcept{ return est * n; }
        };
        map["RankBlueprint"] = MetaEntry{
            [](InFlow in_flow)noexcept{ return AnyFlow::create<RankFlow>(in_flow); }
        };
        map["SourceBlenderBlueprint"] = MetaEntry{
            [](InFlow in_flow)noexcept{ return AnyFlow::create<BlenderFlow>(in_flow); },
            [](double est, size_t)noexcept{ return est; },
            [](double, size_t)noexcept{ return 1.0; }
        };
    }
    bool is_known(const vespalib::string &type) {
        return map.find(type) != map.end();
    }
    const MetaEntry &lookup(const vespalib::string &type) {
        return map.find(type)->second;
    }
};
BlueprintMeta::MetaEntry::~MetaEntry() = default;
BlueprintMeta blueprint_meta;

struct Node {
    vespalib::string  type = "unknown";
    uint32_t          id = 0;
    uint32_t          docid_limit = 0;
    vespalib::string  field_name;
    vespalib::string  query_term;
    bool              strict = false;
    FlowStats         flow_stats = FlowStats(0.0, 0.0, 0.0);
    size_t            count = 0;
    double            self_time_ms = 0.0;
    double            total_time_ms = 0.0;
    double            est_seek = 0.0;
    double            est_cost = 0.0;
    char              seek_type = '?';
    double            ms_per_cost = 0.0;
    double            ms_self_limit = 0.0;
    double            ms_limit = 0.0;
    std::vector<Node> children;
    Node(const Inspector &obj) {
        extract(type, obj["[type]"]);
        type = strip_name(type);
        id = obj["id"].asLong();
        docid_limit = obj["docid_limit"].asLong();
        query_term = obj["query_term"].asString().make_stringview();
        if (query_term.size() > 0) {
            const Inspector &attr = obj["attribute"];
            if (attr.valid()) {
                field_name = attr["name"].asString().make_stringview();
                if (type == "AttributeFieldBlueprint") {
                    type = fmt("Attribute{%s,%s}",
                               attr["type"].asString().make_string().c_str(),
                               attr["fast_search"].asBool() ? "fs" : "lookup");
                }
            } else {
                field_name = obj["field_name"].asString().make_stringview();
                if (type == "DiskTermBlueprint") {
                    type = "DiskTerm";
                }
                if (type == "MemoryTermBlueprint") {
                    type = "MemoryTerm";
                }
            }
        }
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
    vespalib::string name() const {
        vespalib::string res = type;
        if (id > 0) {
            res.append(fmt("[%u]", id));
        }
        if (query_term.size() > 0) {
            if (field_name.size() > 0) {
                res.append(fmt(" %s:%s", field_name.c_str(), query_term.c_str()));
            } else {
                res.append(fmt(" %s", query_term.c_str()));
            }
        }
        return res;
    }
    double rel_count() const {
        return double(count) / docid_limit;
    }
    size_t abs_est_seek() const {
        return double(docid_limit) * est_seek;
    }
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
    void each_node(auto f) {
        f(*this);
        for (auto &child: children) {
            child.each_node(f);
        }
    }
    void calc_cost(InFlow in_flow) {
        if (!children.empty() && !blueprint_meta.is_known(type)) {
            fprintf(stderr, "... blueprint meta-data not found for intermediate node: %s (treating as leaf)\n", name().c_str());
        }
        if (children.empty() || !blueprint_meta.is_known(type)) {
            if (in_flow.strict()) {
                if (!strict) {
                    fprintf(stderr, "... invalid strictness for node: %s\n", name().c_str());
                }
                est_seek = flow_stats.estimate;
                est_cost = flow_stats.strict_cost;
                seek_type = 'S';
            } else if (strict) {
                est_seek = in_flow.rate();
                est_cost = flow::forced_strict_cost(flow_stats, est_seek);
                seek_type = 'F';
            } else {
                est_seek = in_flow.rate();
                est_cost = est_seek * flow_stats.cost;
                seek_type = 'N';
            }
        } else {
            double cost_diff = 0.0;
            double seek_diff = 0.0;
            const auto &meta = blueprint_meta.lookup(type);
            if (in_flow.strict()) {
                if (!strict) {
                    fprintf(stderr, "... invalid strictness for node: %s\n", name().c_str());
                }
                est_seek = flow_stats.estimate;
                seek_type = 'S';
            } else if (strict) {
                cost_diff = flow::strict_cost_diff(flow_stats.estimate, in_flow.rate());
                seek_diff = in_flow.rate() - flow_stats.estimate;
                est_seek = in_flow.rate();
                in_flow.force_strict();
                seek_type = 'F';
            } else {
                est_seek = in_flow.rate();
                seek_type = 'N';
            }
            double flow_cost = 0.0;
            auto flow = meta.make_flow(in_flow);
            for (auto &child: children) {
                child.calc_cost(InFlow(flow.strict(), flow.flow()));
                flow.update_cost(flow_cost, child.est_cost);
                flow.add(child.flow_stats.estimate);
            }
            est_cost = flow_cost + cost_diff;
            if (in_flow.strict()) {
                est_cost += meta.self_cost_strict(flow_stats.estimate, children.size());
            } else {
                est_cost += est_seek * meta.self_cost_non_strict(flow_stats.estimate, children.size());
            }
            if (seek_diff < 0.0) {
                // adjust est_seek for sub-tree
                each_node([factor = est_seek / (est_seek - seek_diff)](Node &node)noexcept{
                              node.est_seek *= factor;
                          });
            }
            if (cost_diff < 0.0) {
                // adjust est_cost for sub-tree
                each_node([factor = est_cost / (est_cost - cost_diff)](Node &node)noexcept{
                              node.est_cost *= factor;
                          });
            }
        }
    }
    void normalize() {
        size_t num_nodes = 0;
        double cost_limit = est_cost * 0.01;
        double time_limit = total_time_ms * 0.01;
        std::vector<double> samples;
        each_node([&](Node &node){
                      ++num_nodes;
                      if (node.est_cost >= cost_limit) {
                          samples.push_back(node.total_time_ms / node.est_cost);
                      }
                  });
        double self_time_limit = total_time_ms * 10.0 / num_nodes;
        double norm_ms_per_cost = samples[samples.size()/2];
        each_node([&](Node &node)noexcept{
                      node.ms_per_cost = norm_ms_per_cost;
                      node.ms_self_limit = self_time_limit;
                      node.ms_limit = time_limit;
                  });
    }
    vespalib::string tingle() const {
        vespalib::string res;
        if (total_time_ms > ms_limit) {
            apply_diff(res, rel_diff(est_seek, rel_count(), 1e-6, 0.50), 's', 'S', 3);
            apply_diff(res, rel_diff(ms_per_cost * est_cost, total_time_ms, 1e-3, 0.50), 't', 'T', 3);
            if (self_time_ms > ms_self_limit) {
                apply_diff(res, rel_diff(self_time_ms, ms_self_limit, 1e-3, 0.01), '+', '*', 1);
            }
        }
        return res;
    }
    void print_header() const {
        fprintf(stdout, "|%10s ", "seeks");
        fprintf(stdout, "|%10s ", "est_seeks");
        fprintf(stdout, "|%11s ", "time_ms");
        fprintf(stdout, "|%11s ", "est_time");
        fprintf(stdout, "|%10s ", "self_ms");
        fprintf(stdout, "|%8s ",  "tingle");
        fprintf(stdout, "|%5s ",  "step");
        fprintf(stdout, "|\n");
    }
    void print_separator() const {
        const char *fill = "-------------------------------------------";
        fprintf(stdout, "+%.10s-", fill);
        fprintf(stdout, "+%.10s-", fill);
        fprintf(stdout, "+%.11s-", fill);
        fprintf(stdout, "+%.11s-", fill);
        fprintf(stdout, "+%.10s-", fill);
        fprintf(stdout, "+%.8s-",  fill);
        fprintf(stdout, "+%.5s-",  fill);
        fprintf(stdout, "+\n");
    }
    void print_stats() const {
        fprintf(stdout, "|%10zu ",  count);
        fprintf(stdout, "|%10zu ",  abs_est_seek());
        fprintf(stdout, "|%11.3f ", total_time_ms);
        fprintf(stdout, "|%11.3f ", ms_per_cost * est_cost);
        fprintf(stdout, "|%10.3f ", self_time_ms);
        fprintf(stdout, "|%8s ", tingle().c_str());
        fprintf(stdout, "|%5c ", seek_type);
        fprintf(stdout, "| ");
    }
    static constexpr const char *pads[4] = {" ├─ "," │  "," └─ ","    "};
    void print_line(const vespalib::string &prefix, const char *pad_self, const char *pad_child) const {
        print_stats();
        fprintf(stdout, "%s%s%s\n", prefix.c_str(), pad_self, name().c_str());
        for (size_t i = 0; i < children.size(); ++i) {
            auto *my_pads = ((i + 1) < children.size()) ? pads : pads + 2;
            children[i].print_line(prefix + pad_child, my_pads[0], my_pads[1]);
        }
    }
    void print() const {
        print_separator();
        print_header();
        print_separator();
        print_line("", "", "");
        print_separator();
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

struct Analyzer {
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
                    if (prof["profiler"].asString().make_stringview() == "tree") {
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
                data.calc_cost(true);
                data.normalize();
                data.print();
                fprintf(stdout, "distribution key: %d, total_time_ms: %g, estimated ms_per_cost: %g\n", key, total_ms, data.ms_per_cost);
                for (auto [type, time]: time_map) {
                    fprintf(stdout, "sample type %s used %g ms total\n", Sample::type_to_str(type).c_str(), time);
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
    Analyzer analyzer;
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

class StdIn : public Input {
private:
    bool _eof = false;
    SimpleBuffer _input;
public:
    Memory obtain() override {
        if ((_input.get().size == 0) && !_eof) {
            WritableMemory buf = _input.reserve(4096);
            ssize_t res = read(STDIN_FILENO, buf.data, buf.size);
            _eof = (res == 0);
            assert(res >= 0); // fail on stdio read errors
            _input.commit(res);
        }
        return _input.obtain();
    }
    Input &evict(size_t bytes) override {
        _input.evict(bytes);
        return *this;
    }
};

int
MyApp::main()
{
    Slime slime;
    std::unique_ptr<Input> input;
    if (file_name == "-") {
        input = std::make_unique<StdIn>();
    } else {
        auto file = std::make_unique<MappedFileInput>(file_name);
        if (!file->valid()) {
            fprintf(stderr, "could not read input file: '%s'\n",
                    file_name.c_str());
            return 1;
        }
        input = std::move(file);
    }
    if(JsonFormat::decode(*input, slime) == 0) {
        fprintf(stderr, "input contains invalid json (%s)\n",
                file_name.c_str());
        return 1;
    }
    analyzer.analyze(slime.get());
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
