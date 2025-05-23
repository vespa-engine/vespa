#! /usr/bin/env python3
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
# coding: utf-8
# /// script
# requires-python = ">=3.10"
# dependencies = [
#     "lightgbm>=4.6.0",
#     "numpy>=2.2.6",
#     "pandas>=2.2.3",
# ]
# ///
# If you have 'uv' installed, you can run this script with:
# `uv run train_lightgbm_categorical.py`
# To ensure correct python version and dependencies without installing anything globally.
import json
import lightgbm as lgb
import numpy as np
import pandas as pd
from pathlib import Path

# Define output paths
MODEL_OUTPUT_PATH = Path("categorical.json")
TESTCASE_OUTPUT_PATH = (
        Path(__file__).parent.parent / "testcases" / "lightgbm" / "categorical_tests.json"
)

np.random.seed(42)

df = pd.DataFrame(
    {
        "feature_1": np.random.random(100),
        "feature_2": np.random.random(100),
        "feature_3": pd.Series(
            np.random.choice(["a", "b", "c"], size=100, replace=True), dtype="category"
        ),
        # Adding more categorical df, with some having null values
        "feature_4": pd.Series(
            np.random.choice(["x", "y", "z", pd.NA], size=100, replace=True),
            dtype="category",
        ),
        "feature_5": pd.Series(
            np.random.choice(["1", "2", "3", pd.NA], size=100, replace=True),
            dtype="category",
        ),
        # Use integer-based categorical df
        "feature_6": pd.Series(
            np.random.choice(["10", "20", "30"], size=100, replace=True),
            dtype="category",
        ),
        "feature_7": pd.Series(
            np.random.choice(["100", "200", "300", pd.NA], size=100, replace=True),
            dtype="category",
        ),
    }
)

# Create a query column: 1 for the first 10 rows, 2 for the next 10, etc.
df["query"] = np.repeat(np.arange(1, 11), 10)

# Create targets with 1 once per query
targets = np.zeros(100)  # Initialize all targets to 0
for i in range(0, 100, 10):
    targets[i + np.random.randint(0, 10)] = (
        1  # Set a random element within each 10-element block to 1
    )
params = {
    "objective": "lambdarank",
    "metric": "ndcg",
    "num_leaves": 50,
    "learning_rate": 0.1,
}

df_train = df.drop(columns=["query"])
groups_col = "query"
group_sizes = df.groupby(groups_col).size().to_numpy()
cat_df = [c for c in df.columns if df[c].dtype.name == "category"]
# Create the LightGBM Dataset with query information
training_set = lgb.Dataset(
    df_train,
    label=targets,
    categorical_feature=cat_df,
    group=group_sizes,
)
# Train the model
model = lgb.train(params, training_set, num_boost_round=5)

# Export model - ensure directory exists
MODEL_OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
with open(MODEL_OUTPUT_PATH, "w") as f:
    json.dump(model.dump_model(), f, indent=2)
    print(f"Model saved to {MODEL_OUTPUT_PATH}")

# Make predictions to be used for testcases
df["model_prediction"] = model.predict(df_train)
testcase_json = df.drop(columns=["query"]).to_json(orient="records")

# Export testcases
TESTCASE_OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
with open(TESTCASE_OUTPUT_PATH, "w") as f:
    json.dump(testcase_json, f, indent=2)
    print(f"Testcases saved to {TESTCASE_OUTPUT_PATH}")
