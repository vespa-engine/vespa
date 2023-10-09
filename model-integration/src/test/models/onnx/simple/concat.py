# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import onnx
from onnx import helper, TensorProto

i_type = helper.make_tensor_value_info('i', TensorProto.FLOAT, [1])
j_type = helper.make_tensor_value_info('j', TensorProto.FLOAT, [1])
k_type = helper.make_tensor_value_info('k', TensorProto.FLOAT, [1])

output_type = helper.make_tensor_value_info('y', TensorProto.FLOAT, [3])

node = onnx.helper.make_node(
    'Concat',
    inputs=['i', 'j', 'k'],
    outputs=['y'],
    axis=0,
)
graph_def = onnx.helper.make_graph(
    nodes = [node],
    name = 'concat_test',
    inputs = [i_type, j_type, k_type],
    outputs = [output_type]
)
model_def = helper.make_model(graph_def, producer_name='concat.py')
onnx.save(model_def, 'concat.onnx')
