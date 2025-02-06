// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package tracedoctor

import (
	"fmt"
	"github.com/vespa-engine/vespa/client/go/internal/vespa/slime"
	"io"
	"strings"
)

type queryNode struct {
	class       string
	fieldName   string
	queryTerm   string
	strict      string
	count       int64
	selfTimeMs  float64
	totalTimeMs float64
	children    []*queryNode
}

func (qn *queryNode) desc() string {
	if qn.queryTerm != "" {
		if qn.fieldName != "" {
			return fmt.Sprintf("%s %s:%s", qn.class, qn.fieldName, qn.queryTerm)
		}
		return fmt.Sprintf("%s %s", qn.class, qn.queryTerm)
	}
	return qn.class
}

type perfDumpCtx struct {
	dst io.Writer
	err error
}

func perfPad(last, self bool) string {
	if !last && !self {
		return "│   "
	}
	if !last {
		return "├── "
	}
	if self {
		return "└── "
	}
	return "    "
}

func (ctx *perfDumpCtx) fmt(format string, args ...interface{}) {
	if ctx.err == nil {
		_, ctx.err = fmt.Fprintf(ctx.dst, format, args...)
	}
}

func (ctx *perfDumpCtx) printSeparator() {
	ctx.fmt("+%s-", strings.Repeat("-", 10))
	ctx.fmt("+%s-", strings.Repeat("-", 10))
	ctx.fmt("+%s-", strings.Repeat("-", 10))
	ctx.fmt("+%s-", strings.Repeat("-", 5))
	ctx.fmt("+\n")
}

func (ctx *perfDumpCtx) printHeader() {
	ctx.fmt("|%10s ", "count")
	ctx.fmt("|%10s ", "total_ms")
	ctx.fmt("|%10s ", "self_ms")
	ctx.fmt("|%5s ", "step")
	ctx.fmt("|\n")
}

func (ctx *perfDumpCtx) printLine(qn *queryNode, prefix, padSelf, padChild string) {
	ctx.fmt("|%10d ", qn.count)
	ctx.fmt("|%10.3f ", qn.totalTimeMs)
	ctx.fmt("|%10.3f ", qn.selfTimeMs)
	ctx.fmt("|%5s ", qn.strict)
	ctx.fmt("|  ")
	ctx.fmt("%s%s%s\n", prefix, padSelf, qn.desc())
	for i, child := range qn.children {
		last := i+1 == len(qn.children)
		ctx.printLine(child, prefix+padChild, perfPad(last, true), perfPad(last, false))
	}
}

func (q *queryNode) Render(output io.Writer) error {
	dst := perfDumpCtx{dst: output}
	dst.printSeparator()
	dst.printHeader()
	dst.printSeparator()
	dst.printLine(q, "", "", "")
	dst.printSeparator()
	return dst.err
}

func extractQueryNode(obj slime.Value) *queryNode {
	res := &queryNode{}
	if obj.Field("strict").AsBool() {
		res.strict = "S"
	} else {
		res.strict = "N"
	}
	if class := obj.Field("[type]"); class.Valid() {
		res.class = stripClassName(class.AsString())
	} else {
		res.class = "<unknown>"
	}
	res.queryTerm = obj.Field("query_term").AsString()
	if res.queryTerm != "" {
		if attr := obj.Field("attribute"); attr.Valid() {
			res.fieldName = attr.Field("name").AsString()
			if res.class == "AttributeFieldBlueprint" {
				caps := "lookup"
				if attr.Field("fast_search").AsBool() {
					caps = "fs"
				}
				res.class = fmt.Sprintf("Attribute{%s,%s}",
					attr.Field("type").AsString(), caps)
			}
		} else {
			res.fieldName = obj.Field("field_name").AsString()
		}
	}
	res.class = strings.TrimSuffix(res.class, "Blueprint")
	childMap := obj.Field("children")
	for i := 0; true; i++ {
		childKey := fmt.Sprintf("[%d]", i)
		if child := childMap.Field(childKey); child.Valid() {
			res.children = append(res.children, extractQueryNode(child))
		} else {
			break
		}
	}
	return res
}

func stripClassName(name string) string {
	end := strings.Index(name, "<")
	if end == -1 {
		end = len(name)
	}
	ns := strings.LastIndex(name[:end], "::")
	var begin int
	if ns == -1 {
		begin = 0
	} else {
		begin = ns + 2
	}
	return name[begin:end]
}

func sampleType(sample slime.Value) string {
	name := sample.Field("name").AsString()
	if strings.HasSuffix(name, "/init") {
		return "init"
	} else if strings.HasSuffix(name, "/seek") {
		return "seek"
	} else if strings.HasSuffix(name, "/unpack") {
		return "unpack"
	} else if strings.HasSuffix(name, "/termwise") {
		return "termwise"
	}
	return "unknown"
}

func samplePath(sample slime.Value) []int {
	var res []int
	name := sample.Field("name").AsString()
	if strings.HasPrefix(name, "/") {
		child := 0
		for pos := 1; pos < len(name); pos++ {
			c := name[pos]
			if c == '/' {
				res = append(res, child)
				child = 0
			} else {
				if c < '0' || c > '9' {
					break
				}
				child = child*10 + int(c-'0')
			}
		}
	}
	return res
}

func (q *queryNode) applySample(sample slime.Value) {
	path := samplePath(sample)
	node := q
	for _, child := range path {
		if child < len(node.children) {
			node = node.children[child]
		} else {
			return
		}
	}
	node.count += sample.Field("count").AsLong()
	totalTime := sample.Field("total_time_ms").AsDouble()
	node.totalTimeMs += totalTime
	if selfTime := sample.Field("self_time_ms"); selfTime.Valid() {
		node.selfTimeMs += selfTime.AsDouble()
	} else {
		node.selfTimeMs += totalTime
	}
}

func hasTag(tag string) func(p *slime.Path, v slime.Value) bool {
	return func(p *slime.Path, v slime.Value) bool {
		return v.Field("tag").AsString() == tag
	}
}

func eachSampleList(list slime.Value, f func(sample slime.Value)) {
	list.EachEntry(func(_ int, sample slime.Value) {
		f(sample)
		eachSampleList(sample.Field("children"), f)
	})
}

func eachSample(prof slime.Value, f func(sample slime.Value)) {
	eachSampleList(prof.Field("roots"), f)
}

func (q *queryNode) ImportMatchPerf(t ThreadTrace) {
	slime.Select(t.root, hasTag("match_profiling"), func(p *slime.Path, v slime.Value) {
		if v.Field("profiler").AsString() == "tree" {
			eachSample(v, func(sample slime.Value) {
				if sampleType(sample) != "unknown" {
					q.applySample(sample)
				}
			})
		}
	})
}

type ThreadTrace struct {
	root slime.Value
}

func (t ThreadTrace) MatchTimeMs() float64 {
	p := slime.Find(t.root, hasTag("match_profiling"))
	if len(p) == 1 {
		return p[0].Apply(t.root).Field("total_time_ms").AsDouble()
	}
	return 0.0
}

type ProtonTrace struct {
	root slime.Value
}

func (p ProtonTrace) FindThreadTraces() []ThreadTrace {
	var traces []ThreadTrace
	slime.Select(p.root, hasTag("query_execution"), func(p *slime.Path, v slime.Value) {
		v.Field("threads").EachEntry(func(idx int, v slime.Value) {
			traces = append(traces, ThreadTrace{v})
		})
	})
	return traces
}

func (p ProtonTrace) ExtractQuery() *queryNode {
	query := slime.Invalid
	plan := slime.Find(p.root, hasTag("query_execution_plan"))
	if len(plan) == 1 {
		query = plan[0].Apply(p.root).Field("optimized")
	}
	return extractQueryNode(query)
}

func (p ProtonTrace) DistributionKey() int64 {
	return p.root.Field("distribution-key").AsLong()
}

func (p ProtonTrace) DocumentType() string {
	return p.root.Field("document-type").AsString()
}

func (p ProtonTrace) DurationMs() float64 {
	return p.root.Field("duration_ms").AsDouble()
}

func FindProtonTraces(root slime.Value) []ProtonTrace {
	var traces []ProtonTrace
	slime.Select(root, func(p *slime.Path, v slime.Value) bool {
		return slime.Valid(v.Field("distribution-key"), v.Field("document-type"), v.Field("duration_ms"))
	}, func(p *slime.Path, v slime.Value) {
		traces = append(traces, ProtonTrace{v})
	})
	return traces
}
