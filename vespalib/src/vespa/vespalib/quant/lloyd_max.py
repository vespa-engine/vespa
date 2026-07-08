# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import math
import scipy


def lloyd_max(f, a, b, m, iters=100):
    """
    A simple implementation of iterative Lloyd-Max for computing the centroids and
    decision thresholds that minimizes mean squared error when quantizing the
    probability distribution `f` into `m` parts.

    :param f: Integratable function object that will be called as f(x) with x in [a, b).
    :param a: Lower bound of function to quantize.
    :param b: Upper bound of the function to quantize.
    :param m: Number of centroids to compute, i.e. the quantization factor.
    :param iters: Number of iterations to run the algorithm for.
    :return: Tuple (centroids array, thresholds array) of sizes (m, m-1).
    """
    # Initial centroid guess: uniformly distributed over the range.
    centroids = [a + ((i - 0.5) / m) * (b - a) for i in range(1, m + 1)]
    thresholds = []
    for i in range(iters):
        # Decision thresholds are always at the half-way point between centroids.
        thresholds = [0.5 * (centroids[q - 1] + centroids[q]) for q in range(1, m)]
        for q in range(m):
            lo = a if q == 0 else thresholds[q - 1]
            hi = b if q == m - 1 else thresholds[q]
            centroids[q] = scipy.integrate.quad(lambda x: x * f(x), lo, hi)[0] / scipy.integrate.quad(f, lo, hi)[0]
    return centroids, thresholds


def unit_norm(z):
    return math.exp((-z ** 2) / 2) / math.sqrt(2 * math.pi)


def compute_codebook_centroids(bits):
    lm = lloyd_max(unit_norm, -20, 20, 2**bits, 1000)
    return lm[0]


if __name__ == "__main__":
    for bits in range(1, 5):
        print(f"{bits}-bit codebook:")
        print(compute_codebook_centroids(bits))
