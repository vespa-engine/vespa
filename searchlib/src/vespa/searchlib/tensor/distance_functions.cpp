// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distance_functions.h"

namespace search::tensor {

template class SquaredEuclideanDistance<float>;
template class SquaredEuclideanDistance<double>;

template class AngularDistance<float>;
template class AngularDistance<double>;

template class InnerProductDistance<float>;
template class InnerProductDistance<double>;

template class GeoDegreesDistance<float>;
template class GeoDegreesDistance<double>;

template class HammingDistance<float>;
template class HammingDistance<double>;

}
