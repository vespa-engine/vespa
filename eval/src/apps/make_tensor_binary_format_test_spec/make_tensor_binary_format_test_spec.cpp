// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/test/test_io.h>
#include <iostream>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::slime::convenience;

using Options = std::initializer_list<std::reference_wrapper<const nbostream>>;
using Dict = std::vector<vespalib::string>;

//-----------------------------------------------------------------------------

template <typename T> std::vector<bool> with_cell_type_opts();
template <> std::vector<bool> with_cell_type_opts<double>() { return {false, true}; }
template <> std::vector<bool> with_cell_type_opts<float>() { return {true}; }

template <typename T> uint8_t cell_type_id();
template <> uint8_t cell_type_id<double>() { return 0; }
template <> uint8_t cell_type_id<float>() { return 1; }

template <typename T> const char *cell_type_str();
template <> const char *cell_type_str<double>() { return ""; }
template <> const char *cell_type_str<float>() { return "<float>"; }

template <typename T> nbostream make_sparse(bool with_cell_type) {
    nbostream data;
    if (with_cell_type) {
        data << uint8_t(0x5);
        data << cell_type_id<T>();
    } else {
        data << uint8_t(0x1);
    }
    return data;
}

template <typename T> nbostream make_dense(bool with_cell_type) {
    nbostream data;
    if (with_cell_type) {
        data << uint8_t(0x6);
        data << cell_type_id<T>();
    } else {
        data << uint8_t(0x2);
    }
    return data;
}

template <typename T> nbostream make_mixed(bool with_cell_type) {
    nbostream data;
    if (with_cell_type) {
        data << uint8_t(0x7);
        data << cell_type_id<T>();
    } else {
        data << uint8_t(0x3);
    }
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
    for (bool with_cell_type: with_cell_type_opts<double>()) {
        TensorSpec spec("double");
        spec.add({{}}, value);
        nbostream sparse = make_sparse<double>(with_cell_type);
        sparse.putInt1_4Bytes(0);
        sparse.putInt1_4Bytes(1);
        sparse << value;
        nbostream dense = make_dense<double>(with_cell_type);
        dense.putInt1_4Bytes(0);
        dense << value;
        nbostream mixed = make_mixed<double>(with_cell_type);
        mixed.putInt1_4Bytes(0);
        mixed.putInt1_4Bytes(0);
        mixed << value;
        set_tensor(test, spec);
        add_binary(test, {sparse, dense, mixed});
        if (value == 0.0) {
            nbostream empty = make_sparse<double>(with_cell_type);
            empty.putInt1_4Bytes(0);
            empty.putInt1_4Bytes(0);
            add_binary(test, empty);
        }
    }
}

//-----------------------------------------------------------------------------

template <typename T>
void make_vector_test(Cursor &test, size_t x_size) {
    for (bool with_cell_type: with_cell_type_opts<T>()) {
        TensorSpec spec(vespalib::make_string("tensor%s(x[%zu])", cell_type_str<T>(), x_size));
        nbostream dense = make_dense<T>(with_cell_type);
        dense.putInt1_4Bytes(1);
        dense.writeSmallString("x");
        dense.putInt1_4Bytes(x_size);
        nbostream mixed = make_mixed<T>(with_cell_type);
        mixed.putInt1_4Bytes(0);
        mixed.putInt1_4Bytes(1);
        mixed.writeSmallString("x");
        mixed.putInt1_4Bytes(x_size);
        for (size_t x = 0; x < x_size; ++x) {
            double value = val(x);
            spec.add({{"x", x}}, value);
            dense << static_cast<T>(value);
            mixed << static_cast<T>(value);
        }
        set_tensor(test, spec);
        add_binary(test, {dense, mixed});
    }
}

template <typename T>
void make_matrix_test(Cursor &test, size_t x_size, size_t y_size) {
    for (bool with_cell_type: with_cell_type_opts<T>()) {
        TensorSpec spec(vespalib::make_string("tensor%s(x[%zu],y[%zu])", cell_type_str<T>(), x_size, y_size));
        nbostream dense = make_dense<T>(with_cell_type);
        dense.putInt1_4Bytes(2);
        dense.writeSmallString("x");
        dense.putInt1_4Bytes(x_size);
        dense.writeSmallString("y");
        dense.putInt1_4Bytes(y_size);
        nbostream mixed = make_mixed<T>(with_cell_type);
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
                dense << static_cast<T>(value);
                mixed << static_cast<T>(value);
            }
        }
        set_tensor(test, spec);
        add_binary(test, {dense, mixed});
    }
}

//-----------------------------------------------------------------------------

template <typename T>
void make_map_test(Cursor &test, const Dict &x_dict_in) {
    for (bool with_cell_type: with_cell_type_opts<T>()) {
        nbostream sparse_base = make_sparse<T>(with_cell_type);
        sparse_base.putInt1_4Bytes(1);
        sparse_base.writeSmallString("x");
        sparse_base.putInt1_4Bytes(x_dict_in.size());
        nbostream mixed_base = make_mixed<T>(with_cell_type);
        mixed_base.putInt1_4Bytes(1);
        mixed_base.writeSmallString("x");
        mixed_base.putInt1_4Bytes(0);
        mixed_base.putInt1_4Bytes(x_dict_in.size());
        auto x_perm = make_permutations(x_dict_in);
        for (const Dict &x_dict: x_perm) {
            TensorSpec spec(vespalib::make_string("tensor%s(x{})", cell_type_str<T>()));
            nbostream sparse = sparse_base;
            nbostream mixed = mixed_base;
            for (vespalib::string x: x_dict) {
                double value = val(x);
                spec.add({{"x", x}}, value);
                sparse.writeSmallString(x);
                mixed.writeSmallString(x);
                sparse << static_cast<T>(value);
                mixed << static_cast<T>(value);
            }
            set_tensor(test, spec);
            add_binary(test, {sparse, mixed});
        }
        if (x_dict_in.empty()) {
            TensorSpec spec(vespalib::make_string("tensor%s(x{})", cell_type_str<T>()));
            set_tensor(test, spec);
            add_binary(test, {sparse_base, mixed_base});
        }
    }
}

template <typename T>
void make_mesh_test(Cursor &test, const Dict &x_dict_in, const vespalib::string &y) {
    for (bool with_cell_type: with_cell_type_opts<T>()) {
        nbostream sparse_base = make_sparse<T>(with_cell_type);
        sparse_base.putInt1_4Bytes(2);
        sparse_base.writeSmallString("x");
        sparse_base.writeSmallString("y");
        sparse_base.putInt1_4Bytes(x_dict_in.size() * 1);
        nbostream mixed_base = make_mixed<T>(with_cell_type);
        mixed_base.putInt1_4Bytes(2);
        mixed_base.writeSmallString("x");
        mixed_base.writeSmallString("y");
        mixed_base.putInt1_4Bytes(0);
        mixed_base.putInt1_4Bytes(x_dict_in.size() * 1);
        auto x_perm = make_permutations(x_dict_in);
        for (const Dict &x_dict: x_perm) {
            TensorSpec spec(vespalib::make_string("tensor%s(x{},y{})", cell_type_str<T>()));
            nbostream sparse = sparse_base;
            nbostream mixed = mixed_base;
            for (vespalib::string x: x_dict) {
                double value = mix({val(x), val(y)});
                spec.add({{"x", x}, {"y", y}}, value);
                sparse.writeSmallString(x);
                sparse.writeSmallString(y);
                mixed.writeSmallString(x);
                mixed.writeSmallString(y);
                sparse << static_cast<T>(value);
                mixed << static_cast<T>(value);
            }
            set_tensor(test, spec);
            add_binary(test, {sparse, mixed});
        }
        if (x_dict_in.empty()) {
            TensorSpec spec(vespalib::make_string("tensor%s(x{},y{})", cell_type_str<T>()));
            set_tensor(test, spec);
            add_binary(test, {sparse_base, mixed_base});
        }
    }
}

//-----------------------------------------------------------------------------

template <typename T>
void make_vector_map_test(Cursor &test,
                          const vespalib::string &mapped_name, const Dict &mapped_dict,
                          const vespalib::string &indexed_name, size_t indexed_size)
{
    for (bool with_cell_type: with_cell_type_opts<T>()) {
        auto type_str = vespalib::make_string("tensor%s(%s{},%s[%zu])", cell_type_str<T>(),
                                              mapped_name.c_str(), indexed_name.c_str(), indexed_size);
        ValueType type = ValueType::from_spec(type_str);
        nbostream mixed_base = make_mixed<T>(with_cell_type);
        mixed_base.putInt1_4Bytes(1);
        mixed_base.writeSmallString(mapped_name);
        mixed_base.putInt1_4Bytes(1);
        mixed_base.writeSmallString(indexed_name);
        mixed_base.putInt1_4Bytes(indexed_size);
        mixed_base.putInt1_4Bytes(mapped_dict.size());
        auto mapped_perm = make_permutations(mapped_dict);
        for (const Dict &dict: mapped_perm) {
            TensorSpec spec(type.to_spec()); // ensures type string is normalized
            nbostream mixed = mixed_base;
            for (vespalib::string label: dict) {
                mixed.writeSmallString(label);
                for (size_t idx = 0; idx < indexed_size; ++idx) {
                    double value = mix({val(label), val(idx)});
                    spec.add({{mapped_name, label}, {indexed_name, idx}}, value);
                    mixed << static_cast<T>(value);
                }
            }
            set_tensor(test, spec);
            add_binary(test, mixed);
        }
        if (mapped_dict.empty()) {
            TensorSpec spec(type.to_spec()); // ensures type string is normalized
            set_tensor(test, spec);
            add_binary(test, mixed_base);
        }
    }
}

//-----------------------------------------------------------------------------

template <typename T> void make_typed_tests(test::TestWriter &writer) {
    make_vector_test<T>(writer.create(), 3);
    make_matrix_test<T>(writer.create(), 2, 3);
    make_map_test<T>(writer.create(), {});
    make_map_test<T>(writer.create(), {"a", "b", "c"});
    make_mesh_test<T>(writer.create(), {}, "a");
    make_mesh_test<T>(writer.create(), {"foo", "bar"}, "a");
    make_vector_map_test<T>(writer.create(), "x", {}, "y", 10);
    make_vector_map_test<T>(writer.create(), "y", {}, "x", 10);
    make_vector_map_test<T>(writer.create(), "x", {"a", "b"}, "y", 3);
    make_vector_map_test<T>(writer.create(), "y", {"a", "b"}, "x", 3);
}

void make_tests(test::TestWriter &writer) {
    make_number_test(writer.create(), 0.0);
    make_number_test(writer.create(), 42.0);
    make_typed_tests<double>(writer);
    make_typed_tests<float>(writer);
}

int main(int, char **) {
    test::StdOut std_out;
    test::TestWriter writer(std_out);
    make_tests(writer);
    return 0;
}
