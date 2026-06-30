// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package tracedoctor

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/client/go/internal/vespa/slime"
)

type testFactory struct{}

func (f testFactory) sampleBase(name string, cnt int64) slime.Value {
	obj := slime.Object()
	obj.Set("name", slime.String(name))
	obj.Set("count", slime.Long(cnt))
	return obj
}

func (f testFactory) treeSample(name string, cnt int64, total float64, self float64) perfSample {
	obj := f.sampleBase(name, cnt)
	obj.Set("total_time_ms", slime.Double(total))
	obj.Set("self_time_ms", slime.Double(self))
	return perfSample{obj}
}

func (f testFactory) leafSample(name string, cnt int64, total float64) perfSample {
	obj := f.sampleBase(name, cnt)
	obj.Set("total_time_ms", slime.Double(total))
	return perfSample{obj}
}

func (f testFactory) flatSample(name string, cnt int64, self float64) perfSample {
	obj := f.sampleBase(name, cnt)
	obj.Set("self_time_ms", slime.Double(self))
	return perfSample{obj}
}

func (f testFactory) dummySample(name string) perfSample {
	return f.treeSample(name, 1, 10, 5)
}

func (f testFactory) simpleQuery() slime.Value {
	return slime.MakeObject(func(obj slime.Value) {
		obj.Set("[type]", slime.String("name::space::AndBlueprint<p1,p2>"))
		obj.Set("id", slime.Long(5))
		obj.Set("strict", slime.Bool(true))
		obj.Set("children", slime.MakeObject(func(obj slime.Value) {
			obj.Set("[0]", slime.MakeObject(func(obj slime.Value) {
				obj.Set("[type]", slime.String("Term"))
				obj.Set("id", slime.Long(4))
				obj.Set("field_name", slime.String("title"))
				obj.Set("query_term", slime.String("foo"))
				obj.Set("strict", slime.Bool(true))
			}))
			obj.Set("[1]", slime.MakeObject(func(obj slime.Value) {
				obj.Set("[type]", slime.String("name::space::OrBlueprint<p1,p2>"))
				obj.Set("id", slime.Long(3))
				obj.Set("strict", slime.Bool(false))
				obj.Set("children", slime.MakeObject(func(obj slime.Value) {
					obj.Set("[0]", slime.MakeObject(func(obj slime.Value) {
						obj.Set("[type]", slime.String("Attribute"))
						obj.Set("id", slime.Long(2))
						obj.Set("query_term", slime.String("42"))
						obj.Set("strict", slime.Bool(false))
						obj.Set("attribute", slime.MakeObject(func(obj slime.Value) {
							obj.Set("name", slime.String("age"))
							obj.Set("type", slime.String("int"))
							obj.Set("fast_search", slime.Bool(true))
						}))
					}))
					obj.Set("[1]", slime.MakeObject(func(obj slime.Value) {
						obj.Set("[type]", slime.String("Attribute"))
						obj.Set("id", slime.Long(1))
						obj.Set("query_term", slime.String("bar"))
						obj.Set("strict", slime.Bool(false))
						obj.Set("attribute", slime.MakeObject(func(obj slime.Value) {
							obj.Set("name", slime.String("city"))
							obj.Set("type", slime.String("string"))
						}))
					}))
				}))
			}))
		}))
	})
}

func TestQueryExtraction(t *testing.T) {
	query := newQueryTree(testFactory{}.simpleQuery())
	assert.Equal(t, len(query.index), 5)
	for i := 1; i <= 5; i++ {
		assert.Equal(t, query.index[i].source.Field("id").AsLong(), int64(i))
	}
	checkNode := func(node *queryNode, id int64, desc string, childCnt int) {
		assert.Equal(t, id, node.source.Field("id").AsLong())
		assert.Equal(t, desc, node.desc())
		assert.Equal(t, childCnt, len(node.children))
	}
	checkNode(query.root, 5, "And[5]", 2)
	checkNode(query.root.children[0], 4, "Term[4] title:foo", 0)
	checkNode(query.root.children[1], 3, "Or[3]", 2)
	checkNode(query.root.children[1].children[0], 2, "Attribute{int,fs}[2] age:42", 0)
	checkNode(query.root.children[1].children[1], 1, "Attribute{string,lookup}[1] city:bar", 0)
}

func TestStripClassName(t *testing.T) {
	tests := []struct {
		input    string
		expected string
	}{
		{"MyClass", "MyClass"},
		{"Namespace::MyClass", "MyClass"},
		{"Outer::Inner::MyClass", "MyClass"},
		{"Outer::Inner::MyClass<int>", "MyClass"},
		{"MyClass<int, double>", "MyClass"},
		{"std::vector<std::string>", "vector"},
		{"::GlobalClass", "GlobalClass"},
	}
	for _, test := range tests {
		result := stripClassName(test.input)
		assert.Equal(t, test.expected, result, "stripClassName(%q)", test.input)
	}
}

func TestPerfSample(t *testing.T) {
	f := testFactory{}
	checkSample := func(p perfSample, name string, cnt int64, total float64, self float64) {
		assert.Equal(t, name, p.name())
		assert.Equal(t, cnt, p.count())
		assert.Equal(t, total, p.totalTimeMs())
		assert.Equal(t, self, p.selfTimeMs())
	}
	checkSample(f.flatSample("my_name", 10, 5), "my_name", 10, 0, 5)
	checkSample(f.treeSample("my_name", 10, 20, 5), "my_name", 10, 20, 5)
	checkSample(f.leafSample("my_name", 10, 5), "my_name", 10, 5, 5)
	type sampleFlags struct {
		legacy, enum, seek, unpack, termwise, mbv bool
	}
	flagCases := []struct {
		name string
		want sampleFlags
	}{
		{"/1/2/And/seek", sampleFlags{legacy: true, seek: true}},
		{"/1/2/And/unpack", sampleFlags{legacy: true, unpack: true}},
		{"/1/2/And/termwise", sampleFlags{legacy: true, termwise: true}},
		{"[3]And::doSeek", sampleFlags{enum: true, seek: true}},
		{"[3]And::doUnpack", sampleFlags{enum: true, unpack: true}},
		{"[3]And::get_hits", sampleFlags{enum: true, termwise: true}},
		{"[3]And::and_hits_into", sampleFlags{enum: true, termwise: true}},
		{"[3]And::or_hits_into", sampleFlags{enum: true, termwise: true}},
		{"[3]MultiBitVectorIterator::doSeek", sampleFlags{enum: true, seek: true, mbv: true}},
		{"bogus", sampleFlags{}},
	}
	for _, c := range flagCases {
		p := f.dummySample(c.name)
		got := sampleFlags{
			legacy:   p.isLegacySample(),
			enum:     p.isEnumSample(),
			seek:     p.isSeekSample(),
			unpack:   p.isUnpackSample(),
			termwise: p.isTermwiseSample(),
			mbv:      p.isMultiBitVectorSample(),
		}
		assert.Equal(t, c.want, got, c.name)
	}
}

func TestQuerySetupPerfSample(t *testing.T) {
	f := testFactory{}
	assert.False(t, f.dummySample("[3]And::create").isFetchPostingsSample())
	assert.True(t, f.dummySample("[3]And::fetchPostings").isFetchPostingsSample())
}

func TestParseNumList(t *testing.T) {
	assert.Equal(t, []int{}, parseNumList("[]", 1, ','))
	assert.Equal(t, []int{3}, parseNumList("[3]", 1, ','))
	assert.Equal(t, []int{3, 5}, parseNumList("[3,5]", 1, ','))
	assert.Equal(t, []int{}, parseNumList("/And", 1, '/'))
	assert.Equal(t, []int{3}, parseNumList("/3/And", 1, '/'))
	assert.Equal(t, []int{3, 5}, parseNumList("/3/5/And", 1, '/'))
}

func TestApplyLegacySample(t *testing.T) {
	f := testFactory{}
	query := newQueryTree(f.simpleQuery())
	assert.True(t, query.applySample(f.treeSample("/X/seek", 3, 11, 3)))
	assert.True(t, query.applySample(f.treeSample("/X/unpack", 7, 9, 7)))
	assert.True(t, query.applySample(f.leafSample("/0/X/init", 10, 12)))
	assert.True(t, query.applySample(f.leafSample("/0/X/termwise", 20, 18)))
	assert.True(t, query.applySample(f.flatSample("/1/1/X/seek", 5, 10)))
	assert.True(t, query.applySample(f.flatSample("/1/1/X/seek", 10, 5)))
	assert.False(t, query.applySample(f.treeSample("/100/X/seek", 10, 20, 10))) // out of bounds
	var counts []int64
	var totals []float64
	var selfs []float64
	query.root.each(func(q *queryNode) { counts = append(counts, q.count) })
	query.root.each(func(q *queryNode) { totals = append(totals, q.totalTimeMs) })
	query.root.each(func(q *queryNode) { selfs = append(selfs, q.selfTimeMs) })
	assert.Equal(t, []int64{10, 30, 0, 0, 15}, counts)
	assert.Equal(t, []float64{20, 30, 0, 0, 0}, totals)
	assert.Equal(t, []float64{10, 30, 0, 0, 15}, selfs)
}

func TestApplySample(t *testing.T) {
	f := testFactory{}
	query := newQueryTree(f.simpleQuery())
	assert.True(t, query.applySample(f.treeSample("[5]X::doSeek", 3, 11, 3)))
	assert.True(t, query.applySample(f.treeSample("[5]X::doUnpack", 7, 9, 7)))
	assert.True(t, query.applySample(f.leafSample("[4]X::initRange", 10, 12)))
	assert.True(t, query.applySample(f.leafSample("[4]X::or_hits_into", 20, 18)))
	assert.True(t, query.applySample(f.flatSample("[1]X::doSeek", 5, 10)))
	assert.True(t, query.applySample(f.flatSample("[1]X::doSeek", 10, 5)))
	assert.False(t, query.applySample(f.treeSample("[100]X::doSeek", 10, 20, 10))) // not found
	assert.False(t, query.applySample(f.treeSample("[]X::doSeek", 10, 20, 10)))    // empty enum list
	var counts []int64
	var totals []float64
	var selfs []float64
	query.root.each(func(q *queryNode) { counts = append(counts, q.count) })
	query.root.each(func(q *queryNode) { totals = append(totals, q.totalTimeMs) })
	query.root.each(func(q *queryNode) { selfs = append(selfs, q.selfTimeMs) })
	assert.Equal(t, []int64{10, 30, 0, 0, 15}, counts)
	assert.Equal(t, []float64{20, 30, 0, 0, 0}, totals)
	assert.Equal(t, []float64{10, 30, 0, 0, 15}, selfs)
}

func TestApplySampleFlags(t *testing.T) {
	f := testFactory{}
	query := newQueryTree(f.simpleQuery())
	assert.True(t, query.applySample(f.flatSample("[4]X::or_hits_into", 1, 0)))
	assert.True(t, query.applySample(f.flatSample("[2]MultiBitVectorIterator::doSeek", 1, 0)))
	assert.True(t, query.index[4].termwise)
	assert.False(t, query.index[4].multiBitVector)
	assert.True(t, query.index[2].multiBitVector)
	assert.False(t, query.index[2].termwise)
	assert.False(t, query.index[5].termwise)
	assert.False(t, query.index[5].multiBitVector)
}

func TestEvalMode(t *testing.T) {
	cases := []struct {
		strict, mbv, taat bool
		want              string
	}{
		{false, false, false, "lazy"},
		{true, false, false, "eager"},
		{true, true, false, "eager mbv"},
		{true, false, true, "eager taat"},
		{true, true, true, "eager mbv taat"},
		{false, true, true, "lazy mbv taat"},
	}
	for _, c := range cases {
		n := &queryNode{strict: c.strict, multiBitVector: c.mbv, termwise: c.taat}
		assert.Equal(t, c.want, n.evalMode())
	}
}

func TestApplyMultiSample(t *testing.T) {
	f := testFactory{}
	query := newQueryTree(f.simpleQuery())
	assert.True(t, query.applySample(f.treeSample("[1,2]X::doSeek", 3, 10, 2)))
	assert.True(t, query.applySample(f.treeSample("[2,3]X::doSeek", 5, 20, 4)))
	assert.True(t, query.applySample(f.treeSample("[2,3]X::doUnpack", 7, 30, 6)))
	assert.True(t, query.applySample(f.treeSample("[5,100]X::doSeek", 10, 10, 10))) // partial
	var counts []int64
	var totals []float64
	var selfs []float64
	query.root.each(func(q *queryNode) { counts = append(counts, q.count) })
	query.root.each(func(q *queryNode) { totals = append(totals, q.totalTimeMs) })
	query.root.each(func(q *queryNode) { selfs = append(selfs, q.selfTimeMs) })
	assert.Equal(t, []int64{10, 0, 12, 15, 3}, counts)
	assert.Equal(t, []float64{5, 0, 25, 30, 5}, totals)
	assert.Equal(t, []float64{5, 0, 5, 6, 1}, selfs)
}

func TestStripNameSpacesKeepSuffix(t *testing.T) {
	tests := []struct {
		input    string
		expected string
	}{
		{"std::vector<custom::Bar>::push_back", "vector<Bar>::push_back"},
		{"my::ns::Type::method", "Type::method"},
		{"Type::func", "Type::func"},
		{"a::b::c::D::E::f", "D::E::f"},
		{"std::map<std::string, foo::Bar>::insert", "map<string, Bar>::insert"},
		{"search::attribute::MultiTermHashFilter<search::queryeval::(anonymous namespace)::IntegerHashFilterWrapper<true> >::doSeek", "MultiTermHashFilter<IntegerHashFilterWrapper<true> >::doSeek"},
		{"Unqualified", "Unqualified"},
	}
	for _, tt := range tests {
		assert.Equal(t, tt.expected, stripNameSpacesKeepSuffix(tt.input))
	}
}

func TestIsObjectArrayAndIteration(t *testing.T) {
	objectArray := slime.MakeObject(func(obj slime.Value) {
		obj.Set("[0]", slime.String("a"))
		obj.Set("[1]", slime.String("b"))
	})
	assert.True(t, isObjectArray(objectArray))
	var result []string
	eachObjectArrayElem(objectArray, func(v slime.Value) {
		result = append(result, v.AsString())
	})
	assert.Equal(t, []string{"a", "b"}, result)

	regularObj := slime.MakeObject(func(obj slime.Value) {
		obj.Set("name", slime.String("test"))
	})
	assert.False(t, isObjectArray(regularObj))
	eachObjectArrayElem(regularObj, func(v slime.Value) {
		t.Fatal("should not be called")
	})
}

func TestStripTemplateParams(t *testing.T) {
	tests := []struct {
		input    string
		expected string
	}{
		{"MultiTermHashFilter<IntegerHashFilterWrapper<true> >::doSeek", "MultiTermHashFilter<...>::doSeek"},
		{"vector<Bar>::push_back", "vector<...>::push_back"},
		{"map<string, Bar>::insert", "map<...>::insert"},
		{"SimpleClass::method", "SimpleClass::method"},
		{"Outer<Inner<T>>::func", "Outer<...>::func"},
		{"Class<A,B,C>::method", "Class<...>::method"},
		{"NoTemplates", "NoTemplates"},
	}
	for _, tt := range tests {
		assert.Equal(t, tt.expected, stripTemplateParams(tt.input))
	}
}

func simpleProtonTrace() protonTrace {
	var source = slime.MakeObject(func(obj slime.Value) {
		obj.Set("traces", slime.MakeArray(func(arr slime.Value) {
			arr.Add(slime.MakeObject(func(obj slime.Value) {
				obj.Set("tag", slime.String("query_setup"))
				obj.Set("traces", slime.MakeArray(func(arr slime.Value) {
					arr.Add(slime.MakeObject(func(obj slime.Value) {
						obj.Set("tag", slime.String("global_filter_decision"))
						obj.Set("parameters", slime.MakeObject(func(obj slime.Value) {
							obj.Set("estimated_hit_ratio", slime.Double(0.5))
						}))
					}))
				}))
			}))
			arr.Add(slime.MakeObject(func(obj slime.Value) {
				obj.Set("tag", slime.String("query_setup_stats"))
				obj.Set("stats", slime.MakeObject(func(obj slime.Value) {
					obj.Set("approximate_nns_distances_computed", slime.Long(123))
				}))
			}))
			arr.Add(slime.MakeObject(func(obj slime.Value) {
				obj.Set("tag", slime.String("query_execution_stats"))
				obj.Set("stats", slime.MakeObject(func(obj slime.Value) {
					obj.Set("exact_nns_distances_computed", slime.Long(456))
				}))
			}))
		}))
	})
	return protonTrace{source: source}
}

func emptyProtonTrace() protonTrace {
	var source = slime.MakeObject(func(obj slime.Value) {
		obj.Set("traces", slime.MakeArray(func(arr slime.Value) {
			arr.Add(slime.MakeObject(func(obj slime.Value) {
				obj.Set("tag", slime.String("query_setup"))
				obj.Set("traces", slime.MakeArray(func(arr slime.Value) {
				}))
			}))
		}))
	})
	return protonTrace{source: source}
}

func protonTraceWithKey(key int64, duration float64) protonTrace {
	return protonTrace{source: slime.MakeObject(func(obj slime.Value) {
		obj.Set("distribution-key", slime.Long(key))
		obj.Set("duration_ms", slime.Double(duration))
	})}
}

func TestCollapseFollowUps(t *testing.T) {
	// duration_ms is unique per trace so we can track individual traces
	traces := []protonTrace{
		protonTraceWithKey(1, 10),
		protonTraceWithKey(2, 20),
		protonTraceWithKey(1, 30),
		protonTraceWithKey(1, 40),
		protonTraceWithKey(2, 50),
	}
	res := collapseFollowUps(traces)

	// one entry per distinct distribution key, keeping the first-seen trace in order
	assert.Equal(t, 2, len(res))
	assert.Equal(t, int64(1), res[0].distributionKey())
	assert.Equal(t, 10.0, res[0].durationMs())
	assert.Equal(t, int64(2), res[1].distributionKey())
	assert.Equal(t, 20.0, res[1].durationMs())

	// later duplicates become follow-ups, preserving their encounter order
	assert.Equal(t, []float64{30, 40}, res[0].followUpDurationsMs())
	assert.Equal(t, []float64{50}, res[1].followUpDurationsMs())
}

func TestCollapseFollowUpsNoDuplicates(t *testing.T) {
	res := collapseFollowUps([]protonTrace{
		protonTraceWithKey(3, 10),
		protonTraceWithKey(1, 20),
		protonTraceWithKey(2, 30),
	})
	// distinct keys are kept as-is, in original order, with no follow-ups
	assert.Equal(t, []float64{10, 20, 30}, []float64{res[0].durationMs(), res[1].durationMs(), res[2].durationMs()})
	assert.Equal(t, []int64{3, 1, 2}, []int64{res[0].distributionKey(), res[1].distributionKey(), res[2].distributionKey()})
	for _, trace := range res {
		assert.Nil(t, trace.followUps)
	}
}

func TestFindValueWhenFound(t *testing.T) {
	var value = simpleProtonTrace().findValueByTag("query_setup_stats")
	assert.NotNil(t, value)
	assert.True(t, value.Valid())
	assert.True(t, value.Field("stats").Field("approximate_nns_distances_computed").Valid())
}

func TestFindValueWhenNotFound(t *testing.T) {
	var value = emptyProtonTrace().findValueByTag("query_setup_stats")
	assert.NotNil(t, value)
	assert.False(t, value.Valid())
}
