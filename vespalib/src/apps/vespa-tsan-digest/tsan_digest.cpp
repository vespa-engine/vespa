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

class SymbolHist {
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
        std::sort(entries.begin(), entries.end(), [](const auto &a, const auto &b)
                  {
                      if (a.second != b.second) {
                          return (a.second > b.second);
                      }
                      return a.first.size() < b.first.size();
                  });
        fprintf(dst, "  hot symbols:\n");
        size_t i = 0;
        size_t worst_score = 0;
        for (; i < entries.size() && i < 5; ++i) {
            worst_score = entries[i].second;
            fprintf(dst, "    %zu: %s\n", worst_score, entries[i].first.c_str());
        }
        size_t overflow = i;
        for (; i < entries.size() && entries[i].second >= worst_score && i - overflow < trace_limit; ++i) {
            fprintf(dst, "    %zu: %s\n", entries[i].second, entries[i].first.c_str());
        }
    }
};

//-----------------------------------------------------------------------------

vespalib::string get_symbol_from_frame(const vespalib::string &frame) {
    auto pos1 = frame.find("#");
    pos1 = frame.find(" ", pos1);
    auto pos2 = frame.rfind(" /");
    if (pos1 == npos) {
        return {};
    }
    if (pos2 == npos) {
        return frame.substr(pos1+1);
    }
    return frame.substr(pos1+1, pos2-pos1-1);
}

void strip_after(vespalib::string &str, const vespalib::string &delimiter) {
    auto pos = str.find(delimiter);
    if (pos != npos) {
        str = str.substr(0, pos);
    }
}

void replace_first(vespalib::string &str, const vespalib::string &old_str, const vespalib::string &new_str) {    
    auto pos = str.find(old_str);
    if (pos != npos) {
        str.replace(pos, old_str.size(), new_str);
    }
}

class StackTrace {
private:
    vespalib::string _heading;
    std::vector<vespalib::string> _frames;
    uint64_t _hash;
    bool _is_read;
    bool _is_write;
public:
    StackTrace(const vespalib::string &heading) noexcept
    : _heading(heading), _frames(), _hash(), _is_read(false), _is_write(false) {}
    ~StackTrace() {}
    void add_frame(const vespalib::string &frame) {
        _frames.push_back(frame);
    }
    void done() {
        strip_after(_heading, " at 0x");
        replace_first(_heading, "Previous", "");
        replace_first(_heading, "Atomic", "atomic");
        replace_first(_heading, "Read", "read"); 
        replace_first(_heading, "Write", "write");
        _is_read = (_heading.find("read") != npos);
        _is_write = (_heading.find("write") != npos);
        _hash = get_hash(_frames);
    }
    bool is_read() const { return _is_read; }
    bool is_write() const { return _is_write; }
    uint64_t hash() const { return _hash; }
    void update(SymbolHist &hist, size_t weight) const {
        for (const auto &frame: _frames) {
            hist.add(get_symbol_from_frame(frame), weight);
        }
    }
    const vespalib::string &heading() const { return _heading; }
    void dump(FILE *dst, const vespalib::string &info) const {
        fprintf(dst, "%s %s\n", _heading.c_str(), info.c_str());
        for (const auto &frame: _frames) {
            fprintf(dst, "%s\n", frame.c_str());
        }
        fprintf(dst, "\n");
    }
};

std::vector<StackTrace> extract_traces(const std::vector<vespalib::string> &lines, size_t cutoff) {
    std::vector<StackTrace> result;
    for (size_t i = 1; (i < lines.size()) && (result.size() < cutoff); ++i) {
        auto pos = lines[i].find("#0 ");
        if (pos != npos) {
            size_t start = i;
            result.emplace_back(lines[i - 1]);
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

size_t raw_reports = 0;

class RawReport : public Report {
private:
    std::vector<vespalib::string> _lines;
    size_t _count;
public:
    RawReport(const std::vector<vespalib::string> &lines)
      : _lines(lines), _count(1)
    {
        ++raw_reports;
    }
    std::vector<vespalib::string> make_keys() const override {
        return {fmt("raw:%" PRIu64, get_hash(_lines))};
    }
    void merge(const Report &) override { ++_count; }
    size_t count() const override { return _count; }
    void dump(FILE *dst) const override {
        for (const auto &line: _lines) {
            fprintf(dst, "%s\n", line.c_str());            
        }
    }
};

size_t write_write_races = 0;

class RaceReport : public Report {
private:
    struct Node {
        StackTrace trace;
        size_t before;
        size_t after;
        size_t count() const { return before + after; }
    };
    std::vector<Node> _nodes;
    size_t _wr;
    size_t _rw;
    size_t _ww;

    void add(const Node &node) {
        for (Node &dst: _nodes) {
            if (dst.trace.hash() == node.trace.hash()) {
                dst.before += node.before;
                dst.after += node.after;
                return;
            }
        }
        _nodes.push_back(node);
    }

public:
    // Note: b happened before a
    RaceReport(const StackTrace &a, const StackTrace &b)
      : _nodes({{a, 0, 1}, {b, 1, 0}}), _wr(0), _rw(0), _ww(0)
    {
        if (b.is_write() && a.is_write()) {
            ++_ww;
            ++write_write_races;
        } else if (b.is_read() && a.is_write()) {
            ++_rw;
        } else if (b.is_write() && a.is_read()) {
            ++_wr;
        } else {
            throw(fmt("invalid race report ('%s' vs '%s')", a.heading().c_str(), b.heading().c_str()));
        }
    }

    std::vector<vespalib::string> make_keys() const override {
        std::vector<vespalib::string> result;
        for (const auto &node: _nodes) {
            result.push_back(fmt("race:%" PRIu64, node.trace.hash()));
        }
        return result;
    }
    void merge(const Report &report) override {
        // should have correct type due to key prefix
        const auto &rhs = dynamic_cast<const RaceReport &>(report);
        _wr += rhs._wr;
        _rw += rhs._rw;
        _ww += rhs._ww;
        for (const auto &node: rhs._nodes) {
            add(node);
        }
    }
    size_t count() const override { return (_wr + _rw + _ww); }
    void dump(FILE *dst) const override {
        std::vector<const Node *> list;
        for (const auto &node: _nodes) {
            list.push_back(&node);
        }
        std::sort(list.begin(), list.end(),
                  [](const auto *a, const auto *b) {
                      return (a->count() > b->count());
                  });
        fprintf(dst, "WARNING: data race cluster with %zu conflicts between %zu traces\n", count(), list.size());
        fprintf(dst, " WR: %zu, RW: %zu, WW: %zu\n", _wr, _rw, _ww);
        SymbolHist sym_hist;
        for (const auto *node: list) {
            node->trace.update(sym_hist, node->count());
            node->trace.dump(dst, fmt("(%zu before, %zu after)", node->before, node->after));
        }
        sym_hist.dump(dst);
    }
};

//-----------------------------------------------------------------------------

using ReportMap = std::map<vespalib::string,Report::SP>;
using MapPos = ReportMap::const_iterator;

size_t total_reports = 0;
ReportMap report_map;
SymbolHist race_sym_hist;

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
        if ((traces.size() == 2) &&
            !(traces[0].is_read() == traces[0].is_write()) &&
            !(traces[1].is_read() == traces[1].is_write()) &&
            (traces[0].is_write() || traces[1].is_write()))
        {
            traces[0].update(race_sym_hist, 1);
            traces[1].update(race_sym_hist, 1);
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
    fprintf(stderr, "found %zu write write races\n", write_write_races);
    fprintf(stderr, "%zu raw reports (unhandled)\n", raw_reports);
    race_sym_hist.dump(stderr);
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
