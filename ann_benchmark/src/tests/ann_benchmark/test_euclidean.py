# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import pytest
import sys
import os
import math
sys.path.insert(0, os.path.abspath("../../vespa/ann_benchmark"))
from vespa_ann_benchmark import DistanceMetric, HnswIndexParams, HnswIndex

class Fixture:
    def __init__(self):
        self.index = HnswIndex(2, HnswIndexParams(16, 200, DistanceMetric.Euclidean, False), False)

    def set(self, lid, value):
       self.index.set_vector(lid, value)

    def get(self, lid):
        return self.index.get_vector(lid)

    def clear(self, lid):
        return self.index.clear_vector(lid)

    def find(self, k, value):
        return self.index.find_top_k(k, value, k + 200)

def test_set_value():
    f = Fixture()
    f.set(0, [1, 2])
    f.set(1, [3, 4])
    assert [1, 2] == f.get(0)
    assert [3, 4] == f.get(1)

def test_clear_value():
    f = Fixture()
    f.set(0, [1, 2])
    assert [1, 2] == f.get(0)
    f.clear(0)
    assert [0, 0] == f.get(0)

def test_find():
    f = Fixture()
    f.set(0, [0, 0])
    f.set(1, [10, 10])
    top = f.find(10, [1, 1])
    assert [top[0][0], top[1][0]] == [0, 1]
    # Allow some rounding errors
    epsilon = 1e-20
    assert abs(top[0][1] - math.sqrt(2)) < epsilon
    assert abs(top[1][1] - math.sqrt(162)) < epsilon
    top2 = f.find(10, [9, 9])
    # Result is not sorted by distance
    assert [top2[0][0], top2[1][0]] == [0, 1]
    assert abs(top2[0][1] - math.sqrt(162)) < epsilon
    assert abs(top2[1][1] - math.sqrt(2)) < epsilon
    assert 0 == f.find(1, [1, 1])[0][0]
    assert 1 == f.find(1, [9, 9])[0][0]
    f.clear(1)
    assert 0 == f.find(1, [9, 9])[0][0]
    assert 0 == f.find(1, [0, 0])[0][0]
    f.clear(0)
    assert 0 == len(f.find(1, [9, 9]))
