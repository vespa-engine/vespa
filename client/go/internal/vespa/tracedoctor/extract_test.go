// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package tracedoctor

import (
	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/client/go/internal/vespa/slime"
	"testing"
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
	checkSampleFlags := func(p perfSample, legacy bool, enum bool, seek bool) {
		assert.Equal(t, enum, p.isEnumSample())
		assert.Equal(t, legacy, p.isLegacySample())
		assert.Equal(t, seek, p.isSeekSample())
	}
	checkSample(f.flatSample("my_name", 10, 5), "my_name", 10, 0, 5)
	checkSample(f.treeSample("my_name", 10, 20, 5), "my_name", 10, 20, 5)
	checkSample(f.leafSample("my_name", 10, 5), "my_name", 10, 5, 5)
	checkSampleFlags(f.dummySample("/1/2/And/seek"), true, false, true)
	checkSampleFlags(f.dummySample("/1/2/And/unpack"), true, false, false)
	checkSampleFlags(f.dummySample("[3]And::doSeek"), false, true, true)
	checkSampleFlags(f.dummySample("[3]And::doUnpack"), false, true, false)
	checkSampleFlags(f.dummySample("bogus"), false, false, false)
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
	query.applySample(f.treeSample("/X/seek", 3, 11, 3))
	query.applySample(f.treeSample("/X/unpack", 7, 9, 7))
	query.applySample(f.leafSample("/0/X/init", 10, 12))
	query.applySample(f.leafSample("/0/X/termwise", 20, 18))
	query.applySample(f.flatSample("/1/1/X/seek", 5, 10))
	query.applySample(f.flatSample("/1/1/X/seek", 10, 5))
	query.applySample(f.treeSample("/100/X/seek", 10, 20, 10)) // out of bounds
	var seeks []int64
	var totals []float64
	var selfs []float64
	query.root.each(func(q *queryNode) { seeks = append(seeks, q.seeks) })
	query.root.each(func(q *queryNode) { totals = append(totals, q.totalTimeMs) })
	query.root.each(func(q *queryNode) { selfs = append(selfs, q.selfTimeMs) })
	assert.Equal(t, []int64{3, 0, 0, 0, 15}, seeks)
	assert.Equal(t, []float64{20, 30, 0, 0, 0}, totals)
	assert.Equal(t, []float64{10, 30, 0, 0, 15}, selfs)
}

func TestApplySample(t *testing.T) {
	f := testFactory{}
	query := newQueryTree(f.simpleQuery())
	query.applySample(f.treeSample("[5]X::doSeek", 3, 11, 3))
	query.applySample(f.treeSample("[5]X::doUnpack", 7, 9, 7))
	query.applySample(f.leafSample("[4]X::initRange", 10, 12))
	query.applySample(f.leafSample("[4]X::or_hits_into", 20, 18))
	query.applySample(f.flatSample("[1]X::doSeek", 5, 10))
	query.applySample(f.flatSample("[1]X::doSeek", 10, 5))
	query.applySample(f.treeSample("[100]X::doSeek", 10, 20, 10)) // not found
	query.applySample(f.treeSample("[]X::doSeek", 10, 20, 10))    // empty enum list
	var seeks []int64
	var totals []float64
	var selfs []float64
	query.root.each(func(q *queryNode) { seeks = append(seeks, q.seeks) })
	query.root.each(func(q *queryNode) { totals = append(totals, q.totalTimeMs) })
	query.root.each(func(q *queryNode) { selfs = append(selfs, q.selfTimeMs) })
	assert.Equal(t, []int64{3, 0, 0, 0, 15}, seeks)
	assert.Equal(t, []float64{20, 30, 0, 0, 0}, totals)
	assert.Equal(t, []float64{10, 30, 0, 0, 15}, selfs)
}

func TestApplyMultiSample(t *testing.T) {
	f := testFactory{}
	query := newQueryTree(f.simpleQuery())
	query.applySample(f.treeSample("[1,2]X::doSeek", 3, 10, 2))
	query.applySample(f.treeSample("[2,3]X::doSeek", 5, 20, 4))
	query.applySample(f.treeSample("[2,3]X::doUnpack", 7, 30, 6))
	var seeks []int64
	var totals []float64
	var selfs []float64
	query.root.each(func(q *queryNode) { seeks = append(seeks, q.seeks) })
	query.root.each(func(q *queryNode) { totals = append(totals, q.totalTimeMs) })
	query.root.each(func(q *queryNode) { selfs = append(selfs, q.selfTimeMs) })
	assert.Equal(t, []int64{0, 0, 5, 8, 3}, seeks)
	assert.Equal(t, []float64{0, 0, 25, 30, 5}, totals)
	assert.Equal(t, []float64{0, 0, 5, 6, 1}, selfs)
}
