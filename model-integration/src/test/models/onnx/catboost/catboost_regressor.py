# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# Generates a CatBoost regressor with CatBoost's own ONNX exporter.
# The export has IR version 3, only the ai.onnx.ml domain and one TreeEnsembleRegressor.
# The regressor trains on synthetic data.
# Its shapes are fixed to [1, 4] -> [1, 1], as in a model exported to score one row at a time.
# The model therefore has no batch dimension.
# Requires catboost (tested with 1.2.10) and onnx.

import os

import catboost
import numpy as np
import onnx

FEATURES = 4
DIR = os.path.dirname(os.path.abspath(__file__))

rng = np.random.default_rng(0)
x = rng.normal(size=(1000, FEATURES)).astype(np.float32)
y = x[:, 0] + 0.5 * x[:, 1] * x[:, 2] - 0.3 * x[:, 3] ** 2

model = catboost.CatBoostRegressor(iterations=10, depth=3, random_seed=0, thread_count=1,
                                   allow_writing_files=False, verbose=0)
model.fit(x, y)
path = os.path.join(DIR, 'catboost_regressor.onnx')
model.save_model(path, format='onnx')

onx = onnx.load(path)
# CatBoost declares [N, 4] -> [N].
# A model exported to score one row at a time declares one row instead.
for value, dims in ((onx.graph.input[0], [1, FEATURES]), (onx.graph.output[0], [1, 1])):
    shape = value.type.tensor_type.shape
    del shape.dim[:]
    for d in dims:
        shape.dim.add().dim_value = d
# The metadata holds per-run values such as a timestamp.
# The producer version holds CatBoost's build details, including paths and an email address.
# Both would make the file differ between runs or publish build details.
del onx.metadata_props[:]
onx.producer_version = catboost.__version__
onnx.checker.check_model(onx)
onnx.save(onx, path)

row = np.array([[1.0, 2.0, 3.0, 4.0]], dtype=np.float32)
print('prediction for', row.tolist(), '=', model.predict(row)[0])
