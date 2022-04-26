// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/size_literals.h>
#include <xxhash.h>
#include <cassert>
#include <vector>
#include <map>
#include <memory>
#include <algorithm>
#include <unistd.h>
#include <string.h>

using vespalib::make_string_short::fmt;

constexpr auto npos = vespalib::string::npos;

//-----------------------------------------------------------------------------

size_t trace_limit = 7;

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
    virtual vespalib::string make_key() const = 0;
    virtual void add(Report::UP report) = 0;
    virtual size_t count() const = 0;
    virtual void dump(FILE *dst) const = 0;
    virtual ~Report() {}
};

class RawReport : public Report {
private:
    std::vector<vespalib::string> _lines;
public:
    RawReport(const std::vector<vespalib::string> &lines)
      : _lines(lines) {}
    vespalib::string make_key() const override {
        return fmt("raw:%zu", get_hash(_lines));
    }
    void add(Report::UP) override {
        fprintf(stderr, "WARNING: hash collision for raw report\n");
    }
    size_t count() const override { return 1; }
    void dump(FILE *dst) const override {
        for (const auto &line: _lines) {
            fprintf(dst, "%s\n", line.c_str());            
        }
    }
};

class RaceReport : public Report {
private:
    StackTrace _trace1;
    StackTrace _trace2;
    size_t _total;
    size_t _inverted;

public:
    RaceReport(const StackTrace &trace1, const StackTrace &trace2)
      : _trace1(trace1), _trace2(trace2), _total(1), _inverted(0) {}

    vespalib::string make_key() const override {
        if (_trace2.hash() < _trace1.hash()) {
            return fmt("race:%zu,%zu", _trace2.hash(), _trace1.hash());
        }
        return fmt("race:%zu,%zu", _trace1.hash(), _trace2.hash());
    }
    void add(Report::UP report) override {
        // should have correct type due to key prefix
        const RaceReport &rhs = dynamic_cast<RaceReport&>(*report);
        ++_total;
        if (_trace1.hash() != rhs._trace1.hash()) {
            ++_inverted;
        }
    }
    size_t count() const override { return _total; }
    void dump(FILE *dst) const override {
        fprintf(dst, "WARNING: ThreadSanitizer: data race\n");
        _trace1.dump(dst);
        _trace2.dump(dst);
        fprintf(dst, "INFO: total: %zu (inverted: %zu)\n", _total, _inverted);
    }
};

//-----------------------------------------------------------------------------

size_t total_reports = 0;
std::map<vespalib::string,Report::UP> reports;

void handle_report(std::unique_ptr<Report> report) {
    ++total_reports;
    auto [pos, first] = reports.try_emplace(report->make_key(), std::move(report));
    if (!first) {
        assert(report && "should still be valid");
        pos->second->add(std::move(report));
    }
}

void make_report(const std::vector<vespalib::string> &lines) {
    auto type = detect_report_type(lines);
    if (type == ReportType::RACE) {
        auto traces = extract_traces(lines, 2);
        if (traces.size() == 2) {
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
    std::vector<Report*> list;
    list.reserve(reports.size());
    for (const auto &[key, value]: reports) {
        list.push_back(value.get());
    }
    std::sort(list.begin(), list.end(),
              [](const auto &a, const auto &b) {
                  return (a->count() > b->count());
              });
    for (const auto *report: list) {
        dump_delimiter(stdout);
        report->dump(stdout);
        dump_delimiter(stdout);
    }
    fprintf(stderr, "%zu reports in, %zu reports out\n", total_reports, reports.size());
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
