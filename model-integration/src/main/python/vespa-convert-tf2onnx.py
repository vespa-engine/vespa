#! /usr/bin/env python3

# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import sys
import onnx

from tf2onnx import convert
from tensorflow.python.tools import saved_model_utils


def find(nodes, test):
    return next((x for x in nodes if test(x)), None)


def make_alias(onnx_model, alias, output_name):
    output = find(onnx_model.graph.output, lambda node: node.name == output_name)
    if output is None:
        print("Could not find output '{}' to alias from '{}'".format(output_name, alias))
        return
    output_tensor = onnx.helper.make_empty_tensor_value_info("")
    output_tensor.CopyFrom(output)
    output_tensor.name = alias
    onnx_model.graph.output.append(output_tensor)
    onnx_model.graph.node.append(onnx.helper.make_node("Identity", [output_name], [alias]))


def verify_outputs(args, onnx_model):
    tag_sets = saved_model_utils.get_saved_model_tag_sets(args.saved_model)
    for tag_set in sorted(tag_sets):
        tag_set = ','.join(tag_set)
        meta_graph_def = saved_model_utils.get_meta_graph_def(args.saved_model, tag_set)
        signature_def_map = meta_graph_def.signature_def
        for signature_def_key in sorted(signature_def_map.keys()):
            outputs_tensor_info = signature_def_map[signature_def_key].outputs
            for output_key, output_tensor in sorted(outputs_tensor_info.items()):
                output_key_exists_as_output = find(onnx_model.graph.output, lambda node: node.name == output_key)
                if output_key_exists_as_output:
                    continue
                make_alias(onnx_model, output_key, output_tensor.name)

    output_names = [ "'{}'".format(o.name) for o in onnx_model.graph.output ]
    print("Outputs in model: {}".format(", ".join(output_names)))


def main():
    convert.main()

    args = convert.get_args()
    onnx_model = onnx.load(args.output)
    verify_outputs(args, onnx_model)
    onnx.save(onnx_model, args.output)


if __name__ == "__main__":
    main()




