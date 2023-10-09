# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import onnx
from onnx import helper, TensorProto

INPUT_1 = helper.make_tensor_value_info('input1', TensorProto.FLOAT, [1])
OUTPUT = helper.make_tensor_value_info('output', TensorProto.INT8, [1])

nodes = [
    helper.make_node(
        'Cast',
        ['input1'],
        ['output'],
        to=TensorProto.INT8
    ),
]
graph_def = helper.make_graph(
    nodes,
    'cast',
    [INPUT_1],
    [OUTPUT],
)
model_def = helper.make_model(graph_def, producer_name='cast_float_int8.py', opset_imports=[onnx.OperatorSetIdProto(version=12)])
onnx.save(model_def, 'cast_float_int8.onnx')
