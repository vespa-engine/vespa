// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package tracedoctor

import (
	"github.com/google/go-cmp/cmp"
	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/client/go/internal/vespa/slime"
	"testing"
)

type testFactory struct{}

func (f testFactory) baseSample(name string, cnt int64) slime.Value {
	obj := slime.Object()
	obj.Set("name", slime.String(name))
	obj.Set("count", slime.Long(cnt))
	return obj
}

func (f testFactory) treeSample(name string, cnt int64, total float64, self float64) slime.Value {
	obj := f.baseSample(name, cnt)
	obj.Set("total_time_ms", slime.Double(total))
	obj.Set("self_time_ms", slime.Double(self))
	return obj
}

func (f testFactory) leafSample(name string, cnt int64, total float64) slime.Value {
	obj := f.baseSample(name, cnt)
	obj.Set("total_time_ms", slime.Double(total))
	return obj
}

func (f testFactory) flatSample(name string, cnt int64, self float64) slime.Value {
	obj := f.baseSample(name, cnt)
	obj.Set("self_time_ms", slime.Double(self))
	return obj
}

func (f testFactory) simpleQuery() *queryNode {
	return &queryNode{
		class:  "And",
		strict: "S",
		children: []*queryNode{
			{
				class:     "Term",
				fieldName: "title",
				queryTerm: "foo",
				strict:    "S",
			},
			{
				class:  "Or",
				strict: "N",
				children: []*queryNode{
					{
						class:     "Attribute{int,fs}",
						fieldName: "age",
						queryTerm: "42",
						strict:    "N",
					},
					{
						class:     "Attribute{string,lookup}",
						fieldName: "city",
						queryTerm: "bar",
						strict:    "N",
					},
				},
			},
		},
	}
}

func (f testFactory) simpleJson() string {
	return `{
		"[type]": "name::space::AndBlueprint<p1,p2>",
		"strict": true,
		"children": {
			"[0]": {
				"[type]": "Term",
				"field_name": "title",
				"query_term": "foo",
				"strict": true
			},
			"[1]": {
				"[type]": "name::space::OrBlueprint<p1,p2>",
				"strict": false,
				"children": {
					"[0]": {
						"[type]": "Attribute",
						"query_term": "42",
						"strict": false,
						"attribute": {
							"name": "age",
							"type": "int",
							"fast_search": true
						}
					},
					"[1]": {
						"[type]": "Attribute",
						"query_term": "bar",
						"strict": false,
						"attribute": {
							"name": "city",
							"type": "string"
						}
					}
				}
			}
		}
	}`
}

func TestQueryExtraction(t *testing.T) {
	f := testFactory{}
	expect := f.simpleQuery()
	input := slime.FromJson(f.simpleJson())
	assert.True(t, input.Valid(), "input is not valid json")
	actual := extractQueryNode(input)
	diff := cmp.Diff(expect, actual, cmp.AllowUnexported(queryNode{}))
	assert.True(t, diff == "", "Query extraction gave unexpected results:\n%s", diff)
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

func TestApplySample(t *testing.T) {
	f := testFactory{}
	query := f.simpleQuery()
	query.applySample(f.treeSample("/X/seek", 3, 11, 3))
	query.applySample(f.treeSample("/X/unpack", 7, 9, 7))
	query.applySample(f.leafSample("/0/X/init", 10, 12))
	query.applySample(f.leafSample("/0/X/termwise", 20, 18))
	query.applySample(f.flatSample("/1/1/X/seek", 5, 10))
	query.applySample(f.flatSample("/1/1/X/seek", 10, 5))
	query.applySample(f.treeSample("bogus", 10, 20, 10))
	query.applySample(f.treeSample("/X/bogus", 10, 20, 10))
	query.applySample(f.treeSample("/100/X/seek", 10, 20, 10)) // out of bounds
	// query.Render(os.Stderr)
	var counts []int64
	var totals []float64
	var selfs []float64
	query.each(func(q *queryNode) { counts = append(counts, q.count) })
	query.each(func(q *queryNode) { totals = append(totals, q.totalTimeMs) })
	query.each(func(q *queryNode) { selfs = append(selfs, q.selfTimeMs) })
	assert.Equal(t, []int64{10, 30, 0, 0, 15}, counts)
	assert.Equal(t, []float64{20, 30, 0, 0, 0}, totals)
	assert.Equal(t, []float64{10, 30, 0, 0, 15}, selfs)
}
