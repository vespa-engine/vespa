#include "eval_fixture.h"
#include "tensor_model.hpp"

namespace vespalib::eval::test {

// for testing of optimizers / tensor functions
// we produce the same param three times:
// as-is, with float cells, and tagged as mutable.
EvalFixture::ParamRepo &
add_variants(EvalFixture::ParamRepo &repo,
             const vespalib::string &name_base,
             const Layout &base_layout,
             const Sequence &seq)
{
    auto name_f = name_base + "_float";
    auto name_m = name_base + "_mutable";
    repo.add(name_base, spec(base_layout, seq), false);
    repo.add(name_f, spec(float_cells(base_layout), seq), false);
    repo.add(name_m, spec(base_layout, seq), true);
    return repo;
}

} // namespace
