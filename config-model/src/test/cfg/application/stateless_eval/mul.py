# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import onnx
from onnx import helper, TensorProto

INPUT_1 = helper.make_tensor_value_info('input1', TensorProto.FLOAT, [1])
INPUT_2 = helper.make_tensor_value_info('input2', TensorProto.FLOAT, [1])
OUTPUT = helper.make_tensor_value_info('output', TensorProto.FLOAT, [1])

nodes = [
    helper.make_node(
        'Mul',
        ['input1', 'input2'],
        ['output'],
    ),
]
graph_def = helper.make_graph(
    nodes,
    'mul',
    [
        INPUT_1,
        INPUT_2
    ],
    [OUTPUT],
)
model_def = helper.make_model(graph_def, producer_name='mul.py', opset_imports=[onnx.OperatorSetIdProto(version=12)])
onnx.save(model_def, 'mul.onnx')
