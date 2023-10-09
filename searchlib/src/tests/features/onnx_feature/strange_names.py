# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import onnx
from onnx import helper, TensorProto

INPUT1 = helper.make_tensor_value_info('input:0', TensorProto.FLOAT, [2])
INPUT2 = helper.make_tensor_value_info('input/1', TensorProto.FLOAT, [2])

OUTPUT1 = helper.make_tensor_value_info('foo/bar', TensorProto.FLOAT, [2])
OUTPUT2 = helper.make_tensor_value_info('-baz:0', TensorProto.FLOAT, [2])

nodes = [
    helper.make_node(
        'Add',
        ['input:0', 'input/1'],
        ['foo/bar'],
    ),
    helper.make_node(
        'Sub',
        ['input:0', 'input/1'],
        ['-baz:0'],
    ),
]
graph_def = helper.make_graph(
    nodes,
    'strange_names',
    [
        INPUT1,
        INPUT2,
    ],
    [
        OUTPUT1,
        OUTPUT2,
    ],
)
model_def = helper.make_model(graph_def, producer_name='strange_names.py', opset_imports=[onnx.OperatorSetIdProto(version=12)])
onnx.save(model_def, 'strange_names.onnx')
