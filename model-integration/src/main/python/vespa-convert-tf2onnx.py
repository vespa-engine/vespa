#! /usr/bin/env python3
# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import onnx
from tf2onnx import convert
from tensorflow.python.tools import saved_model_utils


def find(seq, test, default=None):
    return next((x for x in seq if test(x)), default)


def index_of(seq, elem):
    for i in range(len(seq)):
        if seq[i] == elem:
            return i


def has_initializer(onnx_model, name):
    return find(onnx_model.graph.initializer, lambda i: i.name == name) != None


def has_equivalent_shape(onnx_tensor_shape, tensorflow_tensor_shape):
    onnx_dims = onnx_tensor_shape.dim
    tf_dims = tensorflow_tensor_shape.dim
    if len(onnx_dims) != len(tf_dims):
        return False
    for i in range(len(onnx_dims)):
        onnx_dim_size = onnx_dims[i].dim_value
        tf_dim_size = tf_dims[i].size
        if onnx_dim_size == 0 and tf_dim_size == -1:
            continue
        if onnx_dim_size != tf_dim_size:
            return False
    return True


def find_by_type(seq, tensor):
    return [ item for item in seq if has_equivalent_shape(item.type.tensor_type.shape, tensor.tensor_shape) ]


def rename_output(onnx_model, signature_name, signature_tensor):
    signature_node_name = signature_tensor.name

    graph_output = find(onnx_model.graph.output, lambda output: output.name == signature_node_name)
    if graph_output is None:
        print("TensorFlow signature output '{}' references node '{}' which was not found. Trying to find equivalent output.".format(signature_name, signature_node_name))
        candidates = find_by_type(onnx_model.graph.output, signature_tensor)
        if len(candidates) == 0:
            print("Could not find equivalent output for '{}'. Unable to rename this output.".format(signature_name))
            return
        if len(candidates) > 1:
            print("Found multiple equivalent outputs '{}'. Unable to rename.".format(",".join([ o.name for o in candidates ])))
            return
        graph_output = candidates[0]
        print("Found equivalent output '{}'. Assuming this is correct.".format(graph_output.name))

    if graph_output.name == signature_name:
        print("Signature output '{}' already exists. Skipping.".format(signature_name))
        return

    output_node = find(onnx_model.graph.node, lambda node: graph_output.name in node.output)
    if output_node is None:
        print("Node generating graph output '{}' was not found. Unable to rename.".format(graph_output.name))
        return

    print("Renamed output from '{}' to '{}'".format(graph_output.name, signature_name))
    output_node.output[index_of(output_node.output, graph_output.name)] = signature_name
    graph_output.name = signature_name


def verify_outputs(args, onnx_model):
    tag_sets = saved_model_utils.get_saved_model_tag_sets(args.saved_model)
    for tag_set in tag_sets:
        tag_set = ','.join(tag_set)
        meta_graph_def = saved_model_utils.get_meta_graph_def(args.saved_model, tag_set)
        signature_def_map = meta_graph_def.signature_def
        for signature_def_key in signature_def_map.keys():
            outputs_tensor_info = signature_def_map[signature_def_key].outputs
            for output_key, output_tensor in outputs_tensor_info.items():
                rename_output(onnx_model, output_key, output_tensor)

    print("Inputs in model: {}".format(", ".join(["'{}'".format(o.name) for o in onnx_model.graph.input if not has_initializer(onnx_model, o.name)])))
    print("Outputs in model: {}".format(", ".join(["'{}'".format(o.name) for o in onnx_model.graph.output])))


def main():
    args = convert.get_args()
    convert.main()

    onnx_model = onnx.load(args.output)
    verify_outputs(args, onnx_model)
    onnx.save(onnx_model, args.output)


if __name__ == "__main__":
    main()




