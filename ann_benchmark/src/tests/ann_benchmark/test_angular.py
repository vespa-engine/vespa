# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import pytest
import sys
import os
import math
sys.path.insert(0, os.path.abspath("../../vespa/ann_benchmark"))
from vespa_ann_benchmark import DistanceMetric, HnswIndexParams, HnswIndex

class Fixture:
    def __init__(self, normalize):
        metric = DistanceMetric.InnerProduct if normalize else DistanceMetric.Angular
        self.index = HnswIndex(2, HnswIndexParams(16, 200, metric, False), normalize)
        self.index.set_vector(0, [1, 0])
        self.index.set_vector(1, [10, 10])

    def find(self, k, value):
        return self.index.find_top_k(k, value, k + 200)

    def run_test(self):
        top = self.find(10, [1, 1])
        assert [top[0][0], top[1][0]] == [0, 1]
        # Allow some rounding errors
        epsilon = 6e-8
        assert abs((1 - top[0][1]) - math.sqrt(0.5)) < epsilon
        assert abs((1 - top[1][1]) - 1) < epsilon
        top2 = self.find(10, [0, 2])
        # Result is not sorted by distance
        assert [top2[0][0], top2[1][0]] == [0, 1]
        assert abs((1 - top2[0][1]) - 0) < epsilon
        assert abs((1 - top2[1][1]) - math.sqrt(0.5)) < epsilon
        assert 1 == self.find(1, [1, 1])[0][0]
        assert 0 == self.find(1, [1, -1])[0][0]

def test_find_angular():
    f = Fixture(False)
    f.run_test()

def test_find_angular_normalized():
    f = Fixture(True)
    f.run_test()
