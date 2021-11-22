# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import onnx
from onnx import helper, TensorProto

INPUT1 = helper.make_tensor_value_info('input1', TensorProto.FLOAT, [1])
INPUT2 = helper.make_tensor_value_info('input2', TensorProto.FLOAT, [1])
OUTPUT1 = helper.make_tensor_value_info('output1', TensorProto.FLOAT, [1])
OUTPUT2 = helper.make_tensor_value_info('output2', TensorProto.FLOAT, [1])

nodes = [
    helper.make_node(
        'Mul',
        ['input1', 'input2'],
        ['output1'],
    ),
    helper.make_node(
        'Add',
        ['input1', 'input2'],
        ['output2'],
    ),
]
graph_def = helper.make_graph(
    nodes,
    'mul',
    [
        INPUT1,
        INPUT2
    ],
    [OUTPUT1, OUTPUT2],
)
model_def = helper.make_model(graph_def, producer_name='mul.py', opset_imports=[onnx.OperatorSetIdProto(version=12)])
onnx.save(model_def, 'mul.onnx')
