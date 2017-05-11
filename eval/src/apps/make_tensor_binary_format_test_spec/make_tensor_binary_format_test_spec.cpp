// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value_type.h>
#include <iostream>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::slime::convenience;

using Options = std::initializer_list<std::reference_wrapper<const nbostream>>;
using Dict = std::vector<vespalib::string>;

//-----------------------------------------------------------------------------

nbostream make_sparse() {
    nbostream data;
    data << uint8_t(0x1);
    return data;
}

nbostream make_dense() {
    nbostream data;
    data << uint8_t(0x2);
    return data;
}

nbostream make_mixed() {
    nbostream data;
    data << uint8_t(0x3);
    return data;
}

void set_tensor(Cursor &test, const TensorSpec &spec) {
    const Inspector &old_tensor = test["tensor"];
    if (old_tensor.valid()) {
        TensorSpec old_spec = TensorSpec::from_slime(old_tensor);
        if (!(old_spec == spec)) {
            fprintf(stderr, "inconsistent specs\n");
            std::cerr << old_spec;
            std::cerr << spec;
            abort(); // inconsistent specs across binary permutations
        }
    } else {
        Cursor &tensor = test.setObject("tensor");
        spec.to_slime(tensor);
    }
}

void add_binary(Cursor &test, const nbostream &data) {
    if (!test["binary"].valid()) {
        test.setArray("binary");
    }
    test["binary"].addData(Memory(data.peek(), data.size()));
}

void add_binary(Cursor &test, Options opts) {
    for (const nbostream &opt: opts) {
        add_binary(test, opt);
    }
}

std::vector<Dict> make_permutations(const Dict &dict) {
    std::vector<Dict> list;
    if (dict.empty()) {
    } else if (dict.size() == 1) {
        list.push_back(dict);
    } else if (dict.size() == 2) {
        list.push_back({dict[0], dict[1]});
        list.push_back({dict[1], dict[0]});
    } else if (dict.size() == 3) {
        list.push_back({dict[0], dict[1], dict[2]});
        list.push_back({dict[0], dict[2], dict[1]});
        list.push_back({dict[1], dict[0], dict[2]});
        list.push_back({dict[1], dict[2], dict[0]});
        list.push_back({dict[2], dict[0], dict[1]});
        list.push_back({dict[2], dict[1], dict[0]});
    } else {
        fprintf(stderr, "unsupported permutation size: %zu\n", dict.size());
        abort(); // only implemented for sizes (0,1,2,3)
    }
    return list;
};

const std::map<std::string, double> val_map{
    {"a",    1.0},
    {"b",    2.0},
    {"c",    3.0},
    {"foo",  1.0},
    {"bar",  2.0}};

double val(size_t idx) { return double(idx + 1); }
double val(const vespalib::string &label) {
    auto res = val_map.find(label);
    if (res == val_map.end()) {
        fprintf(stderr, "unsupported label: '%s'\n", label.c_str());
        abort(); // unsupported label
    }
    return res->second;
}
double mix(std::initializer_list<double> vals) {
    double value = 0.0;
    for (double val: vals) {
        value = ((value * 10) + val);
    }
    return value;
}

//-----------------------------------------------------------------------------

void make_number_test(Cursor &test, double value) {
    TensorSpec spec("double");
    spec.add({{}}, value);
    nbostream sparse = make_sparse();
    sparse.putInt1_4Bytes(0);
    sparse.putInt1_4Bytes(1);
    sparse << value;
    nbostream dense = make_dense();
    dense.putInt1_4Bytes(0);
    dense << value;
    nbostream mixed = make_mixed();
    mixed.putInt1_4Bytes(0);
    mixed.putInt1_4Bytes(0);
    mixed << value;
    set_tensor(test, spec);
    add_binary(test, {sparse, dense, mixed});
    if (value == 0.0) {
        nbostream empty = make_sparse();
        empty.putInt1_4Bytes(0);
        empty.putInt1_4Bytes(0);
        add_binary(test, empty);
    }
}

//-----------------------------------------------------------------------------

void make_vector_test(Cursor &test, size_t x_size) {
    TensorSpec spec(vespalib::make_string("tensor(x[%zu])", x_size));
    nbostream dense = make_dense();
    dense.putInt1_4Bytes(1);
    dense.writeSmallString("x");
    dense.putInt1_4Bytes(x_size);
    nbostream mixed = make_mixed();
    mixed.putInt1_4Bytes(0);
    mixed.putInt1_4Bytes(1);
    mixed.writeSmallString("x");
    mixed.putInt1_4Bytes(x_size);
    for (size_t x = 0; x < x_size; ++x) {
        double value = val(x);
        spec.add({{"x", x}}, value);
        dense << value;
        mixed << value;
    }
    set_tensor(test, spec);
    add_binary(test, {dense, mixed});
}

void make_matrix_test(Cursor &test, size_t x_size, size_t y_size) {
    TensorSpec spec(vespalib::make_string("tensor(x[%zu],y[%zu])", x_size, y_size));
    nbostream dense = make_dense();
    dense.putInt1_4Bytes(2);
    dense.writeSmallString("x");
    dense.putInt1_4Bytes(x_size);
    dense.writeSmallString("y");
    dense.putInt1_4Bytes(y_size);
    nbostream mixed = make_mixed();
    mixed.putInt1_4Bytes(0);
    mixed.putInt1_4Bytes(2);
    mixed.writeSmallString("x");
    mixed.putInt1_4Bytes(x_size);
    mixed.writeSmallString("y");
    mixed.putInt1_4Bytes(y_size);
    for (size_t x = 0; x < x_size; ++x) {
        for (size_t y = 0; y < y_size; ++y) {
            double value = mix({val(x), val(y)});
            spec.add({{"x", x}, {"y", y}}, value);
            dense << value;
            mixed << value;
        }
    }
    set_tensor(test, spec);
    add_binary(test, {dense, mixed});
}

//-----------------------------------------------------------------------------

void make_map_test(Cursor &test, const Dict &x_dict_in) {
    TensorSpec spec("tensor(x{})");
    nbostream sparse_base = make_sparse();
    sparse_base.putInt1_4Bytes(1);
    sparse_base.writeSmallString("x");
    sparse_base.putInt1_4Bytes(x_dict_in.size());
    nbostream mixed_base = make_mixed();
    mixed_base.putInt1_4Bytes(1);
    mixed_base.writeSmallString("x");
    mixed_base.putInt1_4Bytes(0);
    mixed_base.putInt1_4Bytes(x_dict_in.size());
    auto x_perm = make_permutations(x_dict_in);
    for (const Dict &x_dict: x_perm) {
        nbostream sparse = sparse_base;
        nbostream mixed = mixed_base;
        for (vespalib::string x: x_dict) {
            double value = val(x);
            spec.add({{"x", x}}, value);
            sparse.writeSmallString(x);
            mixed.writeSmallString(x);
            sparse << value;
            mixed << value;
        }
        set_tensor(test, spec);
        add_binary(test, {sparse, mixed});
    }
    if (x_dict_in.empty()) {
        set_tensor(test, spec);
        add_binary(test, {sparse_base, mixed_base});
    }
}

void make_mesh_test(Cursor &test, const Dict &x_dict_in, const vespalib::string &y) {
    TensorSpec spec("tensor(x{},y{})");
    nbostream sparse_base = make_sparse();
    sparse_base.putInt1_4Bytes(2);
    sparse_base.writeSmallString("x");
    sparse_base.writeSmallString("y");
    sparse_base.putInt1_4Bytes(x_dict_in.size() * 1);
    nbostream mixed_base = make_mixed();
    mixed_base.putInt1_4Bytes(2);
    mixed_base.writeSmallString("x");
    mixed_base.writeSmallString("y");
    mixed_base.putInt1_4Bytes(0);
    mixed_base.putInt1_4Bytes(x_dict_in.size() * 1);
    auto x_perm = make_permutations(x_dict_in);
    for (const Dict &x_dict: x_perm) {
        nbostream sparse = sparse_base;
        nbostream mixed = mixed_base;
        for (vespalib::string x: x_dict) {
            double value = mix({val(x), val(y)});
            spec.add({{"x", x}, {"y", y}}, value);
            sparse.writeSmallString(x);
            sparse.writeSmallString(y);
            mixed.writeSmallString(x);
            mixed.writeSmallString(y);
            sparse << value;
            mixed << value;
        }
        set_tensor(test, spec);
        add_binary(test, {sparse, mixed});
    }
    if (x_dict_in.empty()) {
        set_tensor(test, spec);
        add_binary(test, {sparse_base, mixed_base});
    }
}

//-----------------------------------------------------------------------------

void make_vector_map_test(Cursor &test,
                          const vespalib::string &mapped_name, const Dict &mapped_dict,
                          const vespalib::string &indexed_name, size_t indexed_size)
{
    auto type_str = vespalib::make_string("tensor(%s{},%s[%zu])",
                                          mapped_name.c_str(), indexed_name.c_str(), indexed_size);
    ValueType type = ValueType::from_spec(type_str);
    TensorSpec spec(type.to_spec()); // ensures type string is normalized
    nbostream mixed_base = make_mixed();
    mixed_base.putInt1_4Bytes(1);
    mixed_base.writeSmallString(mapped_name);
    mixed_base.putInt1_4Bytes(1);
    mixed_base.writeSmallString(indexed_name);
    mixed_base.putInt1_4Bytes(indexed_size);
    mixed_base.putInt1_4Bytes(mapped_dict.size());
    auto mapped_perm = make_permutations(mapped_dict);
    for (const Dict &dict: mapped_perm) {
        nbostream mixed = mixed_base;
        for (vespalib::string label: dict) {
            mixed.writeSmallString(label);
            for (size_t idx = 0; idx < indexed_size; ++idx) {
                double value = mix({val(label), val(idx)});
                spec.add({{mapped_name, label}, {indexed_name, idx}}, value);
                mixed << value;
            }
        }
        set_tensor(test, spec);
        add_binary(test, mixed);
    }
    if (mapped_dict.empty()) {
        set_tensor(test, spec);
        add_binary(test, mixed_base);
    }
}

//-----------------------------------------------------------------------------

void make_tests(Cursor &tests) {
    make_number_test(tests.addObject(), 0.0);
    make_number_test(tests.addObject(), 42.0);
    make_vector_test(tests.addObject(), 3);
    make_matrix_test(tests.addObject(), 2, 3);
    make_map_test(tests.addObject(), {});
    make_map_test(tests.addObject(), {"a", "b", "c"});
    make_mesh_test(tests.addObject(), {}, "a");
    make_mesh_test(tests.addObject(), {"foo", "bar"}, "a");
    make_vector_map_test(tests.addObject(), "x", {}, "y", 10);
    make_vector_map_test(tests.addObject(), "y", {}, "x", 10);
    make_vector_map_test(tests.addObject(), "x", {"a", "b"}, "y", 3);
    make_vector_map_test(tests.addObject(), "y", {"a", "b"}, "x", 3);
}

int main(int, char **) {
    Slime slime;
    Cursor &top = slime.setObject();
    Cursor &tests = top.setArray("tests");
    make_tests(tests);
    top.setLong("num_tests", tests.entries());
    fprintf(stdout, "%s", slime.toString().c_str());
    return 0;
}
