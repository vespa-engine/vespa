// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/require.h>
#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/lazy_params.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/feature_name_extractor.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/make_tensor_function.h>
#include <vespa/eval/eval/optimize_tensor_function.h>
#include <vespa/eval/eval/compile_tensor_function.h>
#include <vespa/eval/eval/test/test_io.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/vespalib/data/simple_buffer.h>
#include <vespa/vespalib/data/slime/json_format.h>
#include <vespa/vespalib/io/mapped_file_input.h>
#include <cctype>
#include <set>
#include <map>
#include <vector>

#include <histedit.h>

using vespalib::make_string_short::fmt;

using namespace vespalib::eval;
using namespace vespalib::eval::test;

using vespalib::Slime;
using vespalib::slime::JsonFormat;
using vespalib::slime::Inspector;
using vespalib::slime::Cursor;
using vespalib::Input;
using vespalib::Memory;
using vespalib::SimpleBuffer;

using CostProfile = std::vector<std::pair<size_t,vespalib::duration>>;

const auto &factory = FastValueBuilderFactory::get();

void list_commands(FILE *file, const char *prefix) {
    fprintf(file, "%s'exit' -> exit the program\n", prefix);
    fprintf(file, "%s'help' -> print available commands\n", prefix);
    fprintf(file, "%s'list' -> list named values\n", prefix);
    fprintf(file, "%s'verbose (true|false)' -> enable or disable verbose output\n", prefix);
    fprintf(file, "%s'def <name> <expr>' -> evaluate expression, bind result to a name\n", prefix);
    fprintf(file, "%s'undef <name>' -> remove a named value\n", prefix);    
    fprintf(file, "%s'<expr>' -> evaluate expression\n", prefix);
}

int usage(const char *self) {
    //               -------------------------------------------------------------------------------
    fprintf(stderr, "usage: %s [--verbose] <expr> [expr ...]\n", self);
    fprintf(stderr, "  Evaluate a sequence of expressions. The first expression must be\n");
    fprintf(stderr, "  self-contained (no external values). Later expressions may use the\n");
    fprintf(stderr, "  results of earlier expressions. Expressions are automatically named\n");
    fprintf(stderr, "  using single letter symbols ('a' through 'z'). Quote expressions to\n");
    fprintf(stderr, "  make sure they become separate parameters. The --verbose option may\n");
    fprintf(stderr, "  be specified to get more detailed informaion about how the various\n");
    fprintf(stderr, "  expressions are optimized and executed.\n");
    fprintf(stderr, "\n");
    fprintf(stderr, "example: %s \"2+2\" \"a+2\" \"a+b\"\n", self);
    fprintf(stderr, "  (a=4, b=6, c=10)\n");
    fprintf(stderr, "\n");
    fprintf(stderr, "advanced usage: %s interactive\n", self);
    fprintf(stderr, "  This runs the progam in interactive mode. possible commands (line based):\n");
    list_commands(stderr, "    ");
    fprintf(stderr, "\n");
    fprintf(stderr, "advanced usage: %s json-repl\n", self);
    fprintf(stderr, "  This will put the program into a read-eval-print loop where it reads\n");
    fprintf(stderr, "  json objects from stdin and writes json objects to stdout.\n");
    fprintf(stderr, "  possible commands: (object based)\n");
    fprintf(stderr, "    {expr:<expr>, ?name:<name>, ?verbose:true}\n");
    fprintf(stderr, "    -> { result:<verbatim-expr> ?steps:[{class:string,symbol:string}] }\n");
    fprintf(stderr, "      Evaluate an expression and return the result. If a name is specified,\n");
    fprintf(stderr, "      the result will be bound to that name and will be available as a symbol\n");
    fprintf(stderr, "      when doing future evaluations. Verbose output must be enabled for each\n");
    fprintf(stderr, "      relevant command and will result in the 'steps' field being populated in\n");
    fprintf(stderr, "      the response.\n");
    fprintf(stderr, "  if any command fails, the response will be { error:string }\n");
    fprintf(stderr, "  commands may be batched using json arrays:\n");
    fprintf(stderr, "    [cmd1,cmd2,cmd3] -> [res1,res2,res3]\n");
    fprintf(stderr, "\n");
    //               -------------------------------------------------------------------------------
    return 1;
}

int overflow(int cnt, int max) {
    fprintf(stderr, "error: too many expressions: %d (max is %d)\n", cnt, max);
    return 2;
}

class Context
{
private:
    std::vector<std::string> _param_names;
    std::vector<ValueType>   _param_types;
    std::vector<Value::UP>   _param_values;
    std::vector<Value::CREF> _param_refs;
    bool                     _verbose;
    std::string              _error;
    CTFMetaData              _meta;
    CostProfile              _cost;

    void clear_state() {
        _error.clear();
        _meta = CTFMetaData();
        _cost.clear();
    }

public:
    Context() : _param_names(), _param_types(), _param_values(), _param_refs(), _verbose(), _meta(), _cost() {}
    ~Context();

    void verbose(bool value) { _verbose = value; }
    bool verbose() const { return _verbose; }

    size_t size() const { return _param_names.size(); }
    const std::string &name(size_t idx) const { return _param_names[idx]; }
    const ValueType &type(size_t idx) const { return _param_types[idx]; }

    Value::UP eval(const std::string &expr) {
        clear_state();
        SimpleObjectParams params(_param_refs);
        auto fun = Function::parse(_param_names, expr, FeatureNameExtractor());
        if (fun->has_error()) {
            _error = fmt("expression parsing failed: %s", fun->get_error().c_str());
            return {};
        }
        NodeTypes types = NodeTypes(*fun, _param_types);
        ValueType res_type = types.get_type(fun->root());
        if (res_type.is_error() || !types.errors().empty()) {
            _error = fmt("type resolving failed for expression: '%s'", expr.c_str());
            for (const auto &issue: types.errors()) {
                _error.append(fmt("\n  type issue: %s", issue.c_str()));
            }
            return {};
        }
        vespalib::Stash stash;
        const TensorFunction &plain_fun = make_tensor_function(factory, fun->root(), types, stash);
        const TensorFunction &optimized = optimize_tensor_function(factory, plain_fun, stash);
        Value::UP result;
        if (_verbose) {
            InterpretedFunction ifun(factory, optimized, &_meta);
            REQUIRE_EQ(_meta.steps.size(), ifun.program_size());
            InterpretedFunction::ProfiledContext ctx(ifun);
            result = factory.copy(ifun.eval(ctx, params));
            _cost = ctx.cost;
        } else {
            InterpretedFunction ifun(factory, optimized, nullptr);
            InterpretedFunction::Context ctx(ifun);
            result = factory.copy(ifun.eval(ctx, params));
        }
        REQUIRE_EQ(result->type(), res_type);
        return result;
    }

    const std::string &error() const { return _error; }
    const CTFMetaData &meta() const { return _meta; }
    const CostProfile &cost() const { return _cost; }

    bool save(const std::string &name, Value::UP value) {
        REQUIRE(value);
        for (size_t i = 0; i < _param_names.size(); ++i) {
            if (_param_names[i] == name) {
                _param_types[i] = value->type();
                _param_values[i] = std::move(value);
                _param_refs[i] = *_param_values[i];
                return true;
            }
        }
        _param_names.push_back(name);
        _param_types.push_back(value->type());
        _param_values.push_back(std::move(value));
        _param_refs.emplace_back(*_param_values.back());
        return false;
    }

    bool remove(const std::string &name) {
        for (size_t i = 0; i < _param_names.size(); ++i) {
            if (_param_names[i] == name) {
                _param_names.erase(_param_names.begin() + i);
                _param_types.erase(_param_types.begin() + i);
                _param_values.erase(_param_values.begin() + i);
                _param_refs.erase(_param_refs.begin() + i);
                return true;
            }
        }
        return false;
    }
};
Context::~Context() = default;

void print_error(const std::string &error) {
    fprintf(stderr, "error: %s\n", error.c_str());
}

void print_value(const Value &value, const std::string &name, const CTFMetaData &meta, const CostProfile &cost) {
    bool with_name = !name.empty();
    bool with_meta = !meta.steps.empty();
    auto spec = spec_from_value(value);
    if (with_meta) {
        if (with_name) {
            fprintf(stderr, "meta-data(%s):\n", name.c_str());
        } else {
            fprintf(stderr, "meta-data:\n");
        }
        const auto &steps = meta.steps;
        for (size_t i = 0; i < steps.size(); ++i) {
            fprintf(stderr, "  class: %s\n", steps[i].class_name.c_str());
            fprintf(stderr, "    symbol: %s\n", steps[i].symbol_name.c_str());
            fprintf(stderr, "    count: %zu\n", cost[i].first);
            fprintf(stderr, "    time_us: %g\n", vespalib::count_ns(cost[i].second)/1000.0);
        }
    }
    if (with_name) {
        fprintf(stdout, "%s: ", name.c_str());
    }
    if (value.type().is_double()) {
        fprintf(stdout, "%.32g\n", spec.as_double());
    } else {
        fprintf(stdout, "%s\n", spec.to_string().c_str());
    }
}

void handle_message(Context &ctx, const Inspector &req, Cursor &reply) {
    std::string expr = req["expr"].asString().make_string();
    std::string name = req["name"].asString().make_string();
    ctx.verbose(req["verbose"].asBool());
    if (expr.empty()) {
        reply.setString("error", "missing expression (field name: 'expr')");
        return;
    }
    auto value = ctx.eval(expr);
    if (!value) {
        reply.setString("error", ctx.error());
        return;
    }
    reply.setString("result", spec_from_value(*value).to_expr());
    if (!name.empty()) {
        ctx.save(name, std::move(value));
    }
    if (!ctx.meta().steps.empty()) {
        auto &steps_out = reply.setArray("steps");
        for (const auto &step: ctx.meta().steps) {
            auto &step_out = steps_out.addObject();
            step_out.setString("class", step.class_name);
            step_out.setString("symbol", step.symbol_name);            
        }
    }
}

bool is_hash_bang(const std::string &str) {
    if (str.size() > 2) {
        return str[0] == '#' && str[1] == '!';
    }
    return false;
}

bool is_only_whitespace(const std::string &str) {
    for (auto c : str) {
        if (!std::isspace(static_cast<unsigned char>(c))) {
            return false;
        }
    }
    return true;
}

class Script {
private:
    std::unique_ptr<Input> _input;
    LineReader _reader;
    bool _script_only = false;
public:
    Script(std::unique_ptr<Input> input)
      : _input(std::move(input)), _reader(*_input) {}
    static auto empty() {
        struct EmptyInput : Input {
            Memory obtain() override { return Memory(); }
            Input &evict(size_t) override { return *this; }
        };
        return std::make_unique<Script>(std::make_unique<EmptyInput>());
    }
    static auto from_file(const std::string &file_name) {
        auto input = std::make_unique<vespalib::MappedFileInput>(file_name);
        if (!input->valid()) {
            fprintf(stderr, "warning: could not read script: %s\n", file_name.c_str());
        }
        return std::make_unique<Script>(std::move(input));
    }
    Script &script_only(bool value) {
        _script_only = value;
        return *this;
    }
    bool script_only() const { return _script_only; }
    bool read_line(std::string &line) { return _reader.read_line(line); }
};

class Collector {
private:
    Slime _slime;
    Cursor &_obj;
    Cursor &_arr;
    bool _enabled;
    std::string _error;
public:
    Collector()
      : _slime(), _obj(_slime.setObject()), _arr(_obj.setArray("f")), _enabled(false) {}
    void enable() {
        _enabled = true;
    }
    void fail(const std::string &msg) {
        if (_error.empty()) {
            _error = msg;
        }
    }
    const std::string &error() const {
        return _error;
    }
    void comment(const std::string &text) {
        if (_enabled) {
            Cursor &f = _arr.addObject();
            f.setString("op", "c");
            Cursor &p = f.setObject("p");
            p.setString("t", text);
        }
    }
    void expr(const std::string &name, const std::string &expr) {
        if (_enabled) {
            Cursor &f = _arr.addObject();
            f.setString("op", "e");
            Cursor &p = f.setObject("p");
            p.setString("n", name);
            p.setString("e", expr);
        }
    }
    std::string toString() const {
        return _slime.toString();
    }
    std::string toCompactString() const {
        SimpleBuffer buf;
        JsonFormat::encode(_slime.get(), buf, true);
        return buf.get().make_string();
    }
};

struct EditLineWrapper {
    EditLine *my_el;
    History *my_hist;
    HistEvent ignore;
    Script &script;
    static std::string prompt;
    static char *prompt_fun(EditLine *) { return &prompt[0]; }
    EditLineWrapper(Script &script_in)
      : my_el(el_init("vespa-eval-expr", stdin, stdout, stderr)),
        my_hist(history_init()),
        script(script_in)
    {
        memset(&ignore, 0, sizeof(ignore));
        el_set(my_el, EL_EDITOR, "emacs");
        el_set(my_el, EL_PROMPT, prompt_fun);
        history(my_hist, &ignore, H_SETSIZE, 1024);
        el_set(my_el, EL_HIST, history, my_hist);
    }
    ~EditLineWrapper();
    bool read_line(std::string &line_out) {
        bool from_script = false;
        do {
            from_script = script.read_line(line_out);
            if (!from_script) {
                if (script.script_only()) {
                    return false;
                }
                int line_len = 0;
                const char *line = el_gets(my_el, &line_len);
                if (line == nullptr) {
                    return false;
                }
                line_out.assign(line, line_len);
            }
            if ((line_out.size() > 0) && (line_out[line_out.size() - 1] == '\n')) {
                line_out.pop_back();
            }
        } while (is_hash_bang(line_out) || is_only_whitespace(line_out));
        if (from_script) {
            fprintf(stdout, "%s%s\n", prompt.c_str(), line_out.c_str());
        }
        history(my_hist, &ignore, H_ENTER, line_out.c_str());
        return true;
    }
};
EditLineWrapper::~EditLineWrapper()
{
    el_set(my_el, EL_HIST, history, nullptr);
    history_end(my_hist);
    el_end(my_el);
}
std::string EditLineWrapper::prompt("> ");

const std::string exit_cmd("exit");
const std::string help_cmd("help");
const std::string list_cmd("list");
const std::string verbose_cmd("verbose ");
const std::string def_cmd("def ");
const std::string undef_cmd("undef ");
const std::string ignore_cmd("#");

int interactive_mode(Context &ctx, Script &script, Collector &collector) {
    EditLineWrapper input(script);
    std::string line;
    while (input.read_line(line)) {
        if (line == exit_cmd) {
            return 0;
        }
        if (line == help_cmd) {
            list_commands(stdout, "  ");
            continue;
        }
        if (line == list_cmd) {
            for (size_t i = 0; i < ctx.size(); ++i) {
                fprintf(stdout, "  %s: %s\n", ctx.name(i).c_str(), ctx.type(i).to_spec().c_str());
            }
            continue;
        }
        if (line.find(ignore_cmd) == 0) {
            collector.comment(line.substr(ignore_cmd.size()));
            continue;
        }
        if (line.find(verbose_cmd) == 0) {
            auto flag_str = line.substr(verbose_cmd.size());
            bool flag = (flag_str == "true");
            bool bad = (!flag && (flag_str != "false"));
            if (bad) {
                fprintf(stderr, "bad flag specifier: '%s', must be 'true' or 'false'\n", flag_str.c_str());                
            } else {
                ctx.verbose(flag);
                fprintf(stdout, "verbose set to %s\n", flag ? "true" : "false");
            }
            continue;
        }
        std::string name;
        if (line.find(undef_cmd) == 0) {
            name = line.substr(undef_cmd.size());
            if (ctx.remove(name)) {
                fprintf(stdout, "removed value '%s'\n", name.c_str());
            } else {
                fprintf(stdout, "value not found: '%s'\n", name.c_str());
            }
            collector.fail("undef operation not supported");
            continue;
        }
        std::string expr;
        if (line.find(def_cmd) == 0) {
            auto name_size = (line.find(" ", def_cmd.size()) - def_cmd.size());
            name = line.substr(def_cmd.size(), name_size);
            expr = line.substr(def_cmd.size() + name_size + 1);
        } else {
            expr = line;
        }
        if (ctx.verbose()) {
            fprintf(stderr, "eval '%s'", expr.c_str());
            if (name.empty()) {
                fprintf(stderr, "\n");
            } else {
                fprintf(stderr, " -> '%s'\n", name.c_str());
            }
        }
        collector.expr(name, expr);
        if (auto value = ctx.eval(expr)) {
            print_value(*value, name, ctx.meta(), ctx.cost());
            if (!name.empty()) {
                if (ctx.save(name, std::move(value))) {
                    collector.fail("value redefinition not supported");
                }
            }
        } else {
            collector.fail("sub-expression evaluation failed");
            print_error(ctx.error());
        }
    }
    return 0;
}

int json_repl_mode(Context &ctx) {
    StdIn std_in;
    StdOut std_out;
    for (;;) {
        if (look_for_eof(std_in)) {
            return 0;
        }
        Slime req;
        if (!JsonFormat::decode(std_in, req)) {
            return 3;
        }
        Slime reply;
        if (req.get().type().getId() == vespalib::slime::ARRAY::ID) {
            reply.setArray();
            for (size_t i = 0; i < req.get().entries(); ++i) {
                handle_message(ctx, req[i], reply.get().addObject());
            }
        } else {
            handle_message(ctx, req.get(), reply.setObject());
        }
        write_compact(reply, std_out);
    }
}

// like base64, but replace '/' with '-' and drop padding (note: reserved '+' is still used)
const char *symbols = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+-";
std::map<char,int> make_symbol_map() {
    std::map<char,int> map;
    for (int i = 0; i < 64; ++i) {
        map[symbols[i]] = i;
    }
    return map;
}

// Write bits to url-safe-ish string
struct UrlSafeBitOutput {
    int bits = 0;
    int num_bits = 0;
    std::string result;
    void write_bits(int x, int n) {
        for (int i = 0; i < n; ++i) {
            bits = (bits << 1) | (x & 1);
            if (++num_bits == 6) {
                result.push_back(symbols[bits]);
                num_bits = 0;
                bits = 0;
            }
            x >>= 1;
        }
    }
    void flush() {
        if (num_bits != 0) {
            write_bits(0, 6 - num_bits);
        }
    }
};

// Read bits from url-safe-ish string
struct UrlSafeBitInput {
    int bits = 0;
    int num_bits = 0;
    size_t offset = 0;
    static constexpr int bit_read_mask = (1 << 5);
    static const std::map<char,int> symbol_map;
    const std::string &str;
    UrlSafeBitInput(const std::string &str_in) noexcept : str(str_in) {}
    int read_bits(int n) {
        int x = 0;
        int b = 1;
        for (int i = 0; i < n; ++i) {
            if (num_bits == 0) {
                REQUIRE(offset < str.size()); // input underflow
                auto pos = symbol_map.find(str[offset++]);
                REQUIRE(pos != symbol_map.end()); // invalid input character
                bits = pos->second;
                num_bits = 6;
            }
            if (bits & bit_read_mask) {
                x |= b;
            }
            b <<= 1;
            bits <<= 1;
            --num_bits;
        }
        return x;
    }
};
const std::map<char,int> UrlSafeBitInput::symbol_map = make_symbol_map();

// keeps track of how many bits to use for dict references
struct BitWidthTracker {
    int num;
    int next;
    BitWidthTracker(int num_in, int next_in) noexcept
      : num(num_in), next(next_in) {}
    void use() {
        if (--next == 0) {
            next = 1 << num;
            ++num;
        }
    }
    int width() {
        return num;
    }
};

// unified dictionary satisfying the needs of both compress and decompress
struct LZDict {
    std::map<std::string,int> map;
    std::vector<std::string> list;
    static constexpr int lit8 = 0;
    static constexpr int lit16 = 1;
    static constexpr int eof = 2;
    LZDict() {
        list.push_back("<lit8>");  // 0
        list.push_back("<lit16>"); // 1
        list.push_back("<eof>");   // 2
        // we cannot put these in the forward dictionary since they
        // could produce duplicates which we check for
    }
    int size() { return list.size(); }
    bool has(const std::string &key) {
        return (map.count(key) == 1);
    }
    int add(const std::string &key) {
        REQUIRE(map.count(key) == 0); // no duplicates
        int value = list.size();
        list.push_back(key);
        map[key] = value;
        return value;
    }
    std::string get(int value) {
        REQUIRE(value < size()); // check with size first
        return list[value];
    }
    int get(const std::string &key) {
        REQUIRE(map.count(key) == 1); // check with has first
        return map[key];
    }
};

// ascii-only lz_string compression (https://github.com/pieroxy/lz-string)
void compress_impl(const std::string &str, auto &bits, auto &dict, auto &dst) {

    std::set<std::string> pending;
    std::string ctx_wc;
    std::string ctx_w;

    for (char c: str) {
        std::string ctx_c(1, c);
        if (!dict.has(ctx_c)) {
            dict.add(ctx_c);
            pending.insert(ctx_c);
        }
        ctx_wc = ctx_w + ctx_c;
        if (dict.has(ctx_wc)) {
            ctx_w = ctx_wc;
        } else {
            if (pending.count(ctx_w) == 1) {
                REQUIRE_EQ(ctx_w.size(), 1zu);
                dst.write_bits(dict.lit8, bits.width());
                dst.write_bits(ctx_w[0], 8);
                bits.use();
                pending.erase(ctx_w);
            } else {
                dst.write_bits(dict.get(ctx_w), bits.width());
            }
            bits.use();
            dict.add(ctx_wc);
            ctx_w = ctx_c;
        }
    }
    if (!ctx_w.empty()) {
        if (pending.count(ctx_w) == 1) {
            dst.write_bits(dict.lit8, bits.width());
            dst.write_bits(ctx_w[0], 8);
            bits.use();
            pending.erase(ctx_w);
        } else {
            dst.write_bits(dict.get(ctx_w), bits.width());
        }
        bits.use();
    }
    dst.write_bits(dict.eof, bits.width());
    dst.flush();
}

// ascii-only lz_string decompression (https://github.com/pieroxy/lz-string)
std::string decompress_impl(auto &src, auto &bits, auto &dict) {

    std::string result;

    int c = src.read_bits(2);
    if (c == dict.eof) {
        return result;
    }
    REQUIRE_EQ(c, dict.lit8); // ascii only
    c = src.read_bits(8);
    std::string w(1, char(c));
    result.append(w);
    dict.add(w);

    std::string entry;
    for (;;) {
        c = src.read_bits(bits.width());
        REQUIRE(c != dict.lit16); // ascii only
        if (c == dict.eof) {
            return result;
        }
        if (c == dict.lit8) {
            c = dict.add(std::string(1, char(src.read_bits(8))));
            bits.use();
        }
        REQUIRE(c <= dict.size()); // invalid dict entry
        if (c == dict.size()) {
            entry = w + w.substr(0, 1);
        } else {
            entry = dict.get(c);
        }
        result.append(entry);
        dict.add(w + entry.substr(0, 1));
        bits.use();
        w = entry;
    }
}

// used to encode setups in tensor playground
std::string compress(const std::string &str) {
    LZDict dict;
    BitWidthTracker bits(2, 2);
    UrlSafeBitOutput dst;
    compress_impl(str, bits, dict, dst);
    return dst.result;
}

// used to test the compression code above, hence the inlined REQUIREs
std::string decompress(const std::string &str) {
    LZDict dict;
    BitWidthTracker bits(3, 4);
    UrlSafeBitInput src(str);
    return decompress_impl(src, bits, dict);
}

// What happens during compression and decompression, the full story
struct LZLog {
    static constexpr int BW = 18;
    static constexpr int PW = 14;
    struct Block {
        std::vector<std::string> writer;
        std::vector<std::string> reader;
        void dump(size_t idx) {
            if (writer.empty() && reader.empty()) {
                return;
            }
            size_t len = reader.size() + 1;
            if (idx == 0) {
                len = std::max(len, writer.size());
            } else {
                len = std::max(len, writer.size() + 1);
            }
            size_t wait = (len - writer.size());
            for (size_t i = 0; i < len; ++i) {
                fprintf(stderr, "%*s%-*s%-*s\n",
                        BW, (i >= wait) ? writer[i - wait].c_str() : "",
                        PW, "",
                        BW, (i < reader.size()) ? reader[i].c_str() : "");
            }
        }
    };
    struct Packet {
        int bits;
        int value;
        Packet(int bits_in, int value_in) noexcept
          : bits(bits_in), value(value_in) {}
        void dump() {
            fprintf(stderr, "%*s%-*s%-*s\n",
                    BW, fmt("write %d bits", bits).c_str(),
                    PW, fmt("  -> %4d ->  ", value).c_str(),
                    BW, fmt("read %d bits", bits).c_str());
        }
    };
    std::vector<Block> blocks;
    std::vector<Packet> packets;
    void ensure_block(size_t idx) {
        while (blocks.size() <= idx) {
            blocks.emplace_back();
        }
    }
    void writer(int block, const std::string &msg) {
        ensure_block(block);
        blocks[block].writer.push_back(msg);
    }
    int packet(int block, int bits, int value) {
        if (packets.size() <= size_t(block)) {
            REQUIRE_EQ(packets.size(), size_t(block));
            packets.emplace_back(bits, value);
        } else {
            REQUIRE_EQ(packets[block].bits, bits);
            REQUIRE_EQ(packets[block].value, value);
        }
        return block + 1;
    }
    void reader(int block, const std::string &msg) {
        ensure_block(block);
        blocks[block].reader.push_back(msg);
    }
    void dump() {
        std::string bsep(BW, '-');
        std::string psep(PW, '-');
        REQUIRE_EQ(blocks.size(), packets.size() + 1);
        fprintf(stderr, "%s%s%s\n", bsep.c_str(), psep.c_str(), bsep.c_str());
        fprintf(stderr, "%*s%-*s%-*s\n", BW, "COMPRESS", PW, "     DATA", BW, "DECOMPRESS");
        fprintf(stderr, "%s%s%s\n", bsep.c_str(), psep.c_str(), bsep.c_str());
        for (size_t i = 0; i < blocks.size(); ++i) {
            blocks[i].dump(i);
            if (i < packets.size()) {
                packets[i].dump();
            }
        }
        fprintf(stderr, "%s%s%s\n", bsep.c_str(), psep.c_str(), bsep.c_str());
    }
    ~LZLog();
    struct Writer {
        LZLog &log;
        size_t idx = 0;
        LZDict dict;
        BitWidthTracker bits{2,2};
        UrlSafeBitOutput dst;
        Writer(LZLog &log_in) : log(log_in) {}
        ~Writer();

        static constexpr int lit8 = LZDict::lit8;
        static constexpr int lit16 = LZDict::lit16;
        static constexpr int eof = LZDict::eof;

        int width() { return bits.width(); }
        bool has(const std::string &key) { return dict.has(key); }
        int get(const std::string &key) { return dict.get(key); }

        int add(const std::string &key) {
            int value = dict.add(key);
            log.writer(idx, fmt("dict[%s] -> %d", key.c_str(), value));
            return value;
        }
        void use() {
            int before = bits.width();
            bits.use();
            int after = bits.width();
            log.writer(idx, fmt("bit width %d -> %d", before, after));
        }
        void write_bits(int x, int n) {
            dst.write_bits(x, n);
            idx = log.packet(idx, n, x);
        }
        void flush() {
            dst.flush();
            log.writer(idx, fmt("flush bits"));
        }
    };
    struct Reader {
        LZLog &log;
        size_t idx = 0;
        LZDict dict;
        BitWidthTracker bits{3,4};
        UrlSafeBitInput src;
        Reader(LZLog &log_in, const std::string &str) : log(log_in), src(str) {}
        ~Reader();

        static constexpr int lit8 = LZDict::lit8;
        static constexpr int lit16 = LZDict::lit16;
        static constexpr int eof = LZDict::eof;

        int width() { return bits.width(); }
        int size() { return dict.size(); }
        std::string get(int value) { return dict.get(value); }

        int read_bits(int n) {
            int x = src.read_bits(n);
            idx = log.packet(idx, n, x);
            return x;
        }
        void use() {
            int before = bits.width();
            bits.use();
            int after = bits.width();
            log.reader(idx, fmt("bit width %d -> %d", before, after));
        }
        int add(const std::string &key) {
            int value = dict.add(key);
            log.reader(idx, fmt("dict[%s] -> %d", key.c_str(), value));
            return value;
        }
    };
    static LZLog analyze(const std::string &str) {
        LZLog log;
        Writer writer(log);
        compress_impl(str, writer, writer, writer);
        Reader reader(log, writer.dst.result);
        auto res = decompress_impl(reader, reader, reader);
        REQUIRE_EQ(res, str);
        return log;
    }
};

LZLog::~LZLog() = default;
LZLog::Writer::~Writer() = default;
LZLog::Reader::~Reader() = default;

void verify_compr(std::string str) {
    auto compr = compress(str);
    auto res = decompress(compr);
    REQUIRE_EQ(str, res);
    fprintf(stderr, "'%s' -> '%s' -> '%s'\n", str.c_str(), compr.c_str(), res.c_str());
    auto log = LZLog::analyze(str);
    log.dump();
}

void run_tests() {
    REQUIRE_EQ(strlen(symbols), 64zu);
    verify_compr("");
    verify_compr("abcdef");
    verify_compr("aaaaaa");
    verify_compr("baaaaaa");
    verify_compr("cbaaaaaa");
    verify_compr("ababababababab");
    verify_compr("a and b and c and d");
}

int main(int argc, char **argv) {
    bool verbose = ((argc > 1) && (std::string(argv[1]) == "--verbose"));
    int expr_idx = verbose ? 2 : 1;
    int expr_cnt = (argc - expr_idx);
    int expr_max = ('z' - 'a') + 1;
    if (expr_cnt == 0) {
        return usage(argv[0]);
    }
    if (expr_cnt > expr_max) {
        return overflow(expr_cnt, expr_max);
    }
    Context ctx;
    if ((expr_cnt == 1) && (std::string(argv[expr_idx]) == "interactive")) {
        setlocale(LC_ALL, "");
        Collector ignored;
        return interactive_mode(ctx, *Script::empty(), ignored);
    }
    if ((expr_cnt == 2) && (std::string(argv[expr_idx]) == "interactive")) {
        setlocale(LC_ALL, "");
        Collector ignored;
        return interactive_mode(ctx, *Script::from_file(argv[expr_idx + 1]), ignored);
    }
    if ((expr_cnt == 3) &&
        (std::string(argv[expr_idx]) == "interactive") &&
        (std::string(argv[expr_idx + 2]) == "convert"))
    {
        setlocale(LC_ALL, "");
        Collector collector;
        collector.enable();
        interactive_mode(ctx, Script::from_file(argv[expr_idx + 1])->script_only(true), collector);
        if (collector.error().empty()) {
            fprintf(stdout, "%s\n", collector.toString().c_str());
            return 0;
        } else {
            fprintf(stderr, "conversion failed: %s\n", collector.error().c_str());
            return 3;
        }
    }
    if ((expr_cnt == 3) &&
        (std::string(argv[expr_idx]) == "interactive") &&
        (std::string(argv[expr_idx + 2]) == "link"))
    {
        setlocale(LC_ALL, "");
        Collector collector;
        collector.enable();
        interactive_mode(ctx, Script::from_file(argv[expr_idx + 1])->script_only(true), collector);
        if (collector.error().empty()) {
            auto hash = compress(collector.toCompactString());
            fprintf(stdout, "https://docs.vespa.ai/playground/#%s\n", hash.c_str());
            return 0;
        } else {
            fprintf(stderr, "conversion failed: %s\n", collector.error().c_str());
            return 3;
        }
    }
    if ((expr_cnt == 1) && (std::string(argv[expr_idx]) == "json-repl")) {
        return json_repl_mode(ctx);
    }
    if ((expr_cnt == 1) && (std::string(argv[expr_idx]) == "test")) {
        try {
            run_tests();
        } catch (std::exception &e) {
            fprintf(stderr, "test failed: %s\n", e.what());
            return 3;
        }
        return 0;
    }
    ctx.verbose(verbose);
    std::string name("a");
    for (int i = expr_idx; i < argc; ++i) {
        if (auto value = ctx.eval(argv[i])) {
            if (expr_cnt > 1) {
                print_value(*value, name, ctx.meta(), ctx.cost());
                ctx.save(name, std::move(value));
                ++name[0];
            } else {
                std::string no_name;
                print_value(*value, no_name, ctx.meta(), ctx.cost());
            }
        } else {
            print_error(ctx.error());
            return 3;
        }
    }
    return 0;
}
