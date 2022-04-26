// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/size_literals.h>
#include <xxhash.h>
#include <cassert>
#include <vector>
#include <map>
#include <set>
#include <memory>
#include <algorithm>
#include <unistd.h>
#include <string.h>

using vespalib::make_string_short::fmt;

constexpr auto npos = vespalib::string::npos;

//-----------------------------------------------------------------------------

size_t trace_limit = 9;

//-----------------------------------------------------------------------------

class Hasher {
private:
    XXH3_state_t *_state;
public:
    Hasher() : _state(XXH3_createState()) { assert(_state != nullptr && "Out of memory!"); }
    ~Hasher() { XXH3_freeState(_state); }
    void reset() { XXH3_64bits_reset(_state); }
    void update(const char *buf, size_t len) { XXH3_64bits_update(_state, buf, len); }
    uint64_t get() const { return XXH3_64bits_digest(_state); }
};

uint64_t get_hash(const std::vector<vespalib::string> &list) {
    static Hasher hasher;
    hasher.reset();
    for (const auto &item: list) {
        hasher.update(item.data(), item.size());
    }
    return hasher.get();
}

//-----------------------------------------------------------------------------

class FrameHist {
private:
    std::map<vespalib::string,size_t> _hist;
public:
    void add(const vespalib::string &value, size_t weight) {
        _hist[value] += weight;
    }
    void dump(FILE *dst) {
        std::vector<std::pair<vespalib::string,size_t>> entries;
        for (const auto &entry: _hist) {
            entries.push_back(entry);
        }
        std::sort(entries.begin(), entries.end(), [](const auto &a, const auto &b){
                if (a.second != b.second) {
                    return (a.second > b.second);
                }
                return (a.first < b.first);
            });
        fprintf(dst, "  top rated frames:\n");
        for (size_t i = 0; i < entries.size() && i < trace_limit; ++i) {
            fprintf(dst, "%s -- score: %zu\n", entries[i].first.c_str(), entries[i].second);
        }
    }
};

//-----------------------------------------------------------------------------

class StackTrace {
private:
    vespalib::string _heading;
    std::vector<vespalib::string> _frames;
    uint64_t _hash;
public:
    StackTrace(const vespalib::string &heading) noexcept
    : _heading(heading), _frames(), _hash() {}
    void add_frame(const vespalib::string &frame) {
        _frames.push_back(frame);
    }
    void done() { _hash = get_hash(_frames); }
    uint64_t hash() const { return _hash; }
    void update(FrameHist &hist, size_t weight) const {
        for (const auto &frame: _frames) {
            hist.add(frame, weight);
        }
    }
    void dump(FILE *dst) const {
        fprintf(dst, "%s\n", _heading.c_str());
        for (const auto &frame: _frames) {
            fprintf(dst, "%s\n", frame.c_str());            
        }
        fprintf(dst, "\n");
    }
};

vespalib::string make_trace_heading(const vespalib::string &line) {
    auto pos = line.find(" at 0x");
    if ((pos != npos) && (line.find("Location") == npos)) {
        return line.substr(0, pos);
    }
    return line;
}

std::vector<StackTrace> extract_traces(const std::vector<vespalib::string> &lines, size_t cutoff) {
    std::vector<StackTrace> result;
    for (size_t i = 1; (i < lines.size()) && (result.size() < cutoff); ++i) {
        auto pos = lines[i].find("#0 ");
        if (pos != npos) {
            size_t start = i;
            result.emplace_back(make_trace_heading(lines[i - 1]));
            result.back().add_frame(lines[i]);
            for (i = i + 1; i < lines.size(); ++i) {
                if (((i - start) > trace_limit) ||
                    (lines[i].find("#") == npos))
                {
                    break;
                }
                result.back().add_frame(lines[i]);
            }
            result.back().done();
        }
    }
    return result;
};

//-----------------------------------------------------------------------------

enum class ReportType { UNKNOWN, RACE };

ReportType detect_report_type(const std::vector<vespalib::string> &lines) {
    for (const auto &line: lines) {
        if (starts_with(line, "WARNING: ThreadSanitizer: data race")) {
            return ReportType::RACE;
        }
    }
    return ReportType::UNKNOWN;
}

//-----------------------------------------------------------------------------

bool is_delimiter(const vespalib::string &line) {
    // TSAN delimiter is 18=, look for at least 16=
    return (line.find("================") < line.size());
}

void dump_delimiter(FILE *dst) {
    fprintf(dst, "==================\n");
}

//-----------------------------------------------------------------------------

struct Report {
    using UP = std::unique_ptr<Report>;
    using SP = std::shared_ptr<Report>;
    virtual std::vector<vespalib::string> make_keys() const = 0;
    virtual void merge(const Report &report) = 0;
    virtual size_t count() const = 0;
    virtual void dump(FILE *dst) const = 0;
    virtual ~Report() {}
};

class RawReport : public Report {
private:
    std::vector<vespalib::string> _lines;
    size_t _count;
public:
    RawReport(const std::vector<vespalib::string> &lines)
      : _lines(lines), _count(1) {}
    std::vector<vespalib::string> make_keys() const override {
        return {fmt("raw:%zu", get_hash(_lines))};
    }
    void merge(const Report &) override { ++_count; }
    size_t count() const override { return _count; }
    void dump(FILE *dst) const override {
        for (const auto &line: _lines) {
            fprintf(dst, "%s\n", line.c_str());            
        }
    }
};

class RaceReport : public Report {
private:
    struct Node {
        StackTrace trace;
        size_t count;
    };
    std::vector<Node> _nodes;
    size_t _count;

    void add(const Node &node) {
        for (Node &dst: _nodes) {
            if (dst.trace.hash() == node.trace.hash()) {
                dst.count += node.count;
                return;
            }
        }
        _nodes.push_back(node);
    }

public:
    RaceReport(const StackTrace &a, const StackTrace &b)
      : _nodes({{a, 1}, {b, 1}}), _count(1) {}

    std::vector<vespalib::string> make_keys() const override {
        std::vector<vespalib::string> result;
        for (const auto &node: _nodes) {
            result.push_back(fmt("race:%zu", node.trace.hash()));
        }
        return result;
    }
    void merge(const Report &report) override {
        // should have correct type due to key prefix
        const auto &rhs = dynamic_cast<const RaceReport &>(report);
        _count += rhs._count;
        for (const auto &node: rhs._nodes) {
            add(node);
        }
    }
    size_t count() const override { return _count; }
    void dump(FILE *dst) const override {
        std::vector<const Node *> list;
        for (const auto &node: _nodes) {
            list.push_back(&node);
        }
        std::sort(list.begin(), list.end(),
                  [](const auto *a, const auto *b) {
                      return (a->count > b->count);
                  });
        fprintf(dst, "WARNING: data race cluster with %zu conflicts between %zu traces\n", _count, list.size());
        FrameHist frame_hist;
        for (const auto *node: list) {
            node->trace.update(frame_hist, node->count);
            node->trace.dump(dst);
        }
        frame_hist.dump(dst);
    }
};

//-----------------------------------------------------------------------------

using ReportMap = std::map<vespalib::string,Report::SP>;
using MapPos = ReportMap::const_iterator;

size_t total_reports = 0;
ReportMap report_map;
FrameHist race_frame_hist;

void handle_report(std::unique_ptr<Report> report) {
    ++total_reports;
    auto keys = report->make_keys();
    std::vector<Report::SP> found;
    for (const auto &key: keys) {
        auto pos = report_map.find(key);
        if (pos != report_map.end()) {
            found.push_back(pos->second);
        }
    }
    if (found.empty()) {
        Report::SP my_report = std::move(report);
        for (const auto &key: keys) {
            report_map[key] = my_report;
        }
    } else {
        for (size_t i = 1; i < found.size(); ++i) {
            if (found[0].get() != found[i].get()) {
                found[0]->merge(*found[i]);
            }
        }
        found[0]->merge(*report);
        keys = found[0]->make_keys();
        for (const auto &key: keys) {
            report_map[key] = found[0];
        }
    }
}

void make_report(const std::vector<vespalib::string> &lines) {
    auto type = detect_report_type(lines);
    if (type == ReportType::RACE) {
        auto traces = extract_traces(lines, 2);
        if (traces.size() == 2) {
            traces[0].update(race_frame_hist, 1);
            traces[1].update(race_frame_hist, 1);
            return handle_report(std::make_unique<RaceReport>(traces[0], traces[1]));
        }
    }
    return handle_report(std::make_unique<RawReport>(lines));
}

void handle_line(const vespalib::string &line) {
    static bool inside = false;
    static std::vector<vespalib::string> lines;
    if (is_delimiter(line)) {
        inside = !inside;
        if (!inside && !lines.empty()) {
            make_report(lines);
            lines.clear();
        }
    } else if (inside) {
        lines.push_back(line);
    }
}

void read_input() {
    char buf[64_Ki];
    bool eof = false;
    vespalib::string line;
    while (!eof) {
        ssize_t res = read(STDIN_FILENO, buf, sizeof(buf));
        if (res < 0) {
            throw fmt("error reading stdin: %s", strerror(errno));
        }
        eof = (res == 0);
        for (int i = 0; i < res; ++i) {
            if (buf[i] == '\n') {
                handle_line(line);
                line.clear();
            } else {
                line.push_back(buf[i]);
            }
        }
    }
    if (!line.empty()) {
        handle_line(line);
    }
}

void write_output() {
    std::set<const Report *> seen;
    std::vector<const Report *> list;
    for (const auto &[key, value]: report_map) {
        if (seen.insert(value.get()).second) {
            list.push_back(value.get());
        }
    }
    std::sort(list.begin(), list.end(),
              [](const auto *a, const auto *b) {
                  return (a->count() > b->count());
              });
    for (const auto *report: list) {
        dump_delimiter(stdout);
        report->dump(stdout);
        dump_delimiter(stdout);
    }
    fprintf(stderr, "%zu reports in, %zu reports out\n", total_reports, list.size());
    race_frame_hist.dump(stderr);
}

int main(int, char **) {
    try {
        read_input();
        write_output();
    } catch (vespalib::string &err) {
        fprintf(stderr, "%s\n", err.c_str());
        return 1;
    }
    return 0;
}
