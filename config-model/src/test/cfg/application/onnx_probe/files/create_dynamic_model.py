# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import onnx
import numpy as np
from onnx import helper, TensorProto

INPUT_1 = helper.make_tensor_value_info('input1', TensorProto.FLOAT, ["batch", 2])
INPUT_2 = helper.make_tensor_value_info('input2', TensorProto.FLOAT, ["batch", 2])
OUTPUT = helper.make_tensor_value_info('out', TensorProto.FLOAT, ["batch", "dim1", "dim2"])

SHAPE = helper.make_tensor('shape', TensorProto.INT64, dims=[3], vals=np.array([1,2,2]).astype(np.int64))

nodes = [
    helper.make_node('Concat', ['input1', 'input2'], ['concat'], axis=1),
    helper.make_node('Reshape', ['concat', 'shape'], ['out']),
]
graph_def = helper.make_graph(nodes, 'simple_scoring', [INPUT_1, INPUT_2], [OUTPUT], [SHAPE])
model_def = helper.make_model(graph_def, producer_name='create_dynamic_model.py', opset_imports=[onnx.OperatorSetIdProto(version=12)])
onnx.save(model_def, 'dynamic_model_2.onnx')
