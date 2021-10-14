// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
namespace config {

namespace internal {

template<typename T, typename Converter>
VectorInserter<T, Converter>::VectorInserter(std::vector<T> & vector)
    : _vector(vector)
{}

template<typename T, typename Converter>
void
VectorInserter<T, Converter>::entry(size_t idx, const ::vespalib::slime::Inspector & inspector)
{
    (void) idx;
    Converter converter;
    _vector.push_back(converter(inspector));
}

} // namespace internal

}
