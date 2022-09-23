// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchsummary/docsummary/slime_filler_filter.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::docsummary::SlimeFillerFilter;

namespace {

class WrappedIterator {
    SlimeFillerFilter::Iterator _iterator;
public:
    WrappedIterator(const SlimeFillerFilter::Iterator& iterator)
        : _iterator(iterator)
    {
    }
    WrappedIterator check_render(vespalib::stringref field_name) {
        auto iterator = _iterator.check_field(field_name);
        EXPECT_TRUE(iterator.should_render());
        return iterator;
    }
    WrappedIterator check_block(vespalib::stringref field_name) {
        auto iterator = _iterator.check_field(field_name);
        EXPECT_FALSE(iterator.should_render());
        return iterator;
    }
};

}

class SlimeFillerFilterTest : public testing::Test
{
    std::unique_ptr<SlimeFillerFilter> _filter;

    WrappedIterator get_filter() {
        return _filter ? _filter->begin() : SlimeFillerFilter::all();
    }
protected:
    SlimeFillerFilterTest();
    ~SlimeFillerFilterTest() override;

    void drop_filter() { _filter.reset(); }
    void reset_filter() { _filter = std::make_unique<SlimeFillerFilter>(); }

    WrappedIterator check_render(vespalib::stringref field_name) {
        return get_filter().check_render(field_name);
    }
    WrappedIterator check_block(vespalib::stringref field_name) {
        return get_filter().check_block(field_name);
    }
    void check_render_no_sub_fields() {
        check_block("a");
        check_block("b");
        check_block("c");
        check_block("d");
    }
    void check_render_all_sub_fields() {
        check_render("a").check_render("c");
        check_render("b").check_render("c").check_render("d");
        check_render("c");
        check_render("b").check_render("d");
    }
    void check_render_some_sub_fields() {
        check_render("a").check_render("c");
        check_render("b").check_render("c").check_render("d");
        check_block("c");
        check_render("b").check_block("d");
    }

public:
    SlimeFillerFilterTest& add(vespalib::stringref field_path) {
        if (_filter) {
            _filter->add(field_path);
        }
        return *this;
    }

    SlimeFillerFilterTest& add_remaining(vespalib::stringref field_path) {
        SlimeFillerFilter::add_remaining(_filter, field_path);
        return *this;
    }
};

SlimeFillerFilterTest::SlimeFillerFilterTest()
    : testing::Test(),
      _filter(std::make_unique<SlimeFillerFilter>())
{
}

SlimeFillerFilterTest::~SlimeFillerFilterTest() = default;

TEST_F(SlimeFillerFilterTest, block_everything_or_nothing)
{
    check_render_no_sub_fields();
    drop_filter();
    check_render_all_sub_fields();
    reset_filter();
    check_render_no_sub_fields();
}

TEST_F(SlimeFillerFilterTest, filter_filters_sub_fields)
{
    add("a").add("b.c");
    check_render_some_sub_fields();
}

TEST_F(SlimeFillerFilterTest, short_paths_shadows_longer_paths)
{
    add("a").add("a.f").add("b.c");
    check_render_some_sub_fields();
    reset_filter();
    add("a.f").add("a").add("b.c");
    check_render_some_sub_fields();
}

TEST_F(SlimeFillerFilterTest, simple_remaining_path_allows_all_sub_fields)
{
    add_remaining("z");
    check_render_all_sub_fields();
}

TEST_F(SlimeFillerFilterTest, composite_remainig_paths_filter_sub_fields)
{
    add_remaining("z.a").add_remaining("z.b.c");
    check_render_some_sub_fields();
}

TEST_F(SlimeFillerFilterTest, short_remaining_path_shadows_longer_remaining_path)
{
    add_remaining("z").add_remaining("z.k");
    check_render_all_sub_fields();
    reset_filter();
    add_remaining("z.k").add_remaining("z");
    check_render_all_sub_fields();
    reset_filter();
    add_remaining("z.a").add_remaining("z.a.f").add_remaining("z.b.c");
    check_render_some_sub_fields();
    reset_filter();
    add_remaining("z.a.f").add_remaining("z.a").add_remaining("z.b.c");
    check_render_some_sub_fields();
}

GTEST_MAIN_RUN_ALL_TESTS()
