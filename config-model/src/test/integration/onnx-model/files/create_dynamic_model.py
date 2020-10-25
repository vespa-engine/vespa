# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import onnx
from onnx import helper, TensorProto

INPUT = helper.make_tensor_value_info('input', TensorProto.FLOAT, ["batch", "sequence"])
OUTPUT = helper.make_tensor_value_info('output', TensorProto.FLOAT, ["batch", "sequence"])

nodes = [helper.make_node('Identity', ['input'], ['output'])]
graph_def = helper.make_graph( nodes, 'simple_scoring', [INPUT], [OUTPUT])
model_def = helper.make_model(graph_def, producer_name='create_dynamic_model.py')
onnx.save(model_def, 'dynamic_model.onnx')
