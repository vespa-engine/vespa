# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import onnx
import numpy as np
from onnx import helper, TensorProto

data_type = helper.make_tensor_value_info('data', TensorProto.FLOAT, [3,2])
indices_type = helper.make_tensor_value_info('indices', TensorProto.FLOAT, [2,2])
output_type = helper.make_tensor_value_info('y', TensorProto.FLOAT, [2,2,2])

node = onnx.helper.make_node(
    'Gather',
    inputs=['data', 'indices'],
    outputs=['y'],
    axis=0,
)
graph_def = onnx.helper.make_graph(
    nodes = [node],
    name = 'gather_test',
    inputs = [data_type, indices_type],
    outputs = [output_type]
)
model_def = helper.make_model(graph_def, producer_name='gather.py')
onnx.save(model_def, 'gather.onnx')
