# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import onnx
from onnx import helper, TensorProto

input = helper.make_tensor_value_info('input', TensorProto.FLOAT, [3])
output = helper.make_tensor_value_info('output', TensorProto.FLOAT, [3])

initializers = [
    helper.make_tensor(
        name='epsilon',  # small constant: no dimensions
        data_type=TensorProto.FLOAT,
        dims=(),
        vals=[1e-6]
    )
]

nodes = [
    onnx.helper.make_node(
        'Exp',
        inputs=['input'],
        outputs=['exp_output']
    ),
    onnx.helper.make_node(
        'ReduceSum',
        inputs=['exp_output'],
        outputs=['sum_exp_output'],
        axes=[0]
    ),
    onnx.helper.make_node(
        'Add',
        inputs=['sum_exp_output', 'epsilon'],
        outputs=['add_output']
    ),
    onnx.helper.make_node(
        'Div',
        inputs=['exp_output', 'add_output'],
        outputs=['output']
    )
]

graph_def = onnx.helper.make_graph(
    nodes = nodes,
    name = 'test',
    inputs = [input],
    outputs = [output],
    initializer = initializers
)
model_def = helper.make_model(graph_def, producer_name='small_constants_and_functions.py')
onnx.checker.check_model(model_def)
onnx.save(model_def, 'small_constants_and_functions.onnx')
