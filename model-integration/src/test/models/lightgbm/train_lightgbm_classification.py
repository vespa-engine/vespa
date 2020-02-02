#! /usr/bin/env python3
# coding: utf-8

import json
import random

import lightgbm as lgb
import numpy as np
import pandas as pd


def category_value(arr):
    values = { np.NaN: 0, "a":1, "b":2, "c":3, "d":4, "e":5, "i":1, "j":2, "k":3, "l":4, "m":5 }
    return np.array([ 0.21 * values[i] for i in arr ])

# Create training set
num_examples = 100000
missing_prob = 0.01
features = pd.DataFrame({
                "numerical_1":   np.random.random(num_examples),
                "numerical_2":   np.random.random(num_examples),
                "categorical_1": pd.Series(np.random.permutation(["a", "b", "c", "d", "e"] * int(num_examples/5)), dtype="category"),
                "categorical_2": pd.Series(np.random.permutation(["i", "j", "k", "l", "m"] * int(num_examples/5)), dtype="category"),
           })

# randomly insert missing values
for i in range(int(num_examples * len(features.columns) * missing_prob)):
    features.loc[random.randint(0, num_examples-1), features.columns[random.randint(0, len(features.columns)-1)]] = None

# create targets (with 0.0 as default for missing values)
target = features["numerical_1"] + features["numerical_2"] + category_value(features["categorical_1"]) + category_value(features["categorical_2"])
target = (target > 2.24) * 1.0
lgb_train = lgb.Dataset(features, target)

# Train model
params = {
    'objective': 'binary',
    'metric': 'binary_logloss',
    'num_leaves': 3,
}
model = lgb.train(params, lgb_train, num_boost_round=5)

# Save model
with open("classification.json", "w") as f:
    json.dump(model.dump_model(), f, indent=2)

# Predict (for comparison with Vespa evaluation)
predict_features = pd.DataFrame({
    "numerical_1":   pd.Series([  None, 0.1, None,  0.7]),
    "numerical_2":   pd.Series([np.NaN, 0.2,  0.5,  0.8]),
    "categorical_1": pd.Series([  None, "a",  "b", None], dtype="category"),
    "categorical_2": pd.Series([  None, "i",  "j",  "m"], dtype="category"),
    })
print(model.predict(predict_features))
