# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import onnx
from onnx import helper, TensorProto

IN1 = helper.make_tensor_value_info('in1', TensorProto.FLOAT, [-1, 'inner'])
IN2 = helper.make_tensor_value_info('in2', TensorProto.FLOAT, ['outer', -1])
OUT1 = helper.make_tensor_value_info('out1', TensorProto.FLOAT, [-1, 'inner'])
OUT2 = helper.make_tensor_value_info('out2', TensorProto.FLOAT, ['outer', -1])
OUT3 = helper.make_tensor_value_info('out3', TensorProto.FLOAT, [-1, -1])

nodes = [
    helper.make_node(
        'Add',
        ['in1', 'in2'],
        ['out1'],
    ),
    helper.make_node(
        'Sub',
        ['in1', 'in2'],
        ['out2'],
    ),
    helper.make_node(
        'Mul',
        ['in1', 'in2'],
        ['out3'],
    ),
]
graph_def = helper.make_graph(
    nodes,
    'probe_model',
    [IN1, IN2],
    [OUT1, OUT2, OUT3],
)
model_def = helper.make_model(graph_def, producer_name='probe_model.py', opset_imports=[onnx.OperatorSetIdProto(version=12)])
onnx.save(model_def, 'probe_model.onnx')
