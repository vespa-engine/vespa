#include "eval_fixture.h"
#include "tensor_model.hpp"

namespace vespalib::eval::test {

// for testing of optimizers / tensor functions
// we produce the same param three times:
// as-is, with float cells, and tagged as mutable.
void add_variants(EvalFixture::ParamRepo &repo,
                  const vespalib::string &name_base,
                  const Layout &base_layout,
                  const Sequence &seq)
{
    auto name_f = name_base + "_f";
    auto name_m = "@" + name_base;
    auto name_m_f = "@" + name_base + "_f";
    repo.add(name_base, spec(base_layout, seq), false);
    repo.add(name_m, spec(base_layout, seq), true);
    repo.add(name_f, spec(float_cells(base_layout), seq), false);
    repo.add(name_m_f, spec(float_cells(base_layout), seq), true);
}

} // namespace
