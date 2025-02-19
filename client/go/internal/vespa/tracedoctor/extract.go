// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package tracedoctor

import (
	"fmt"
	"github.com/vespa-engine/vespa/client/go/internal/vespa/slime"
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

func (q *queryNode) each(f func(q *queryNode)) {
	f(q)
	for _, child := range q.children {
		child.each(f)
	}
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
	dst *output
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
	ctx.dst.fmt(format, args...)
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

func (q *queryNode) render(output *output) {
	dst := perfDumpCtx{dst: output}
	dst.printSeparator()
	dst.printHeader()
	dst.printSeparator()
	dst.printLine(q, "", "", "")
	dst.printSeparator()
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
			if strings.Contains(res.class, "Attribute") {
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

func isMatchSample(sample slime.Value) bool {
	name := sample.Field("name").AsString()
	return strings.HasPrefix(name, "/") &&
		(strings.HasSuffix(name, "/init") ||
			strings.HasSuffix(name, "/seek") ||
			strings.HasSuffix(name, "/unpack") ||
			strings.HasSuffix(name, "/termwise"))
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
	if !isMatchSample(sample) {
		return
	}
	node := q
	path := samplePath(sample)
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

func (q *queryNode) importMatchPerf(t threadTrace) {
	slime.Select(t.root, hasTag("match_profiling"), func(p *slime.Path, v slime.Value) {
		eachSample(v, func(sample slime.Value) {
			q.applySample(sample)
		})
	})
}

type threadTrace struct {
	root slime.Value
}

func (t threadTrace) matchTimeMs() float64 {
	p := slime.Find(t.root, hasTag("match_profiling"))
	if len(p) == 1 {
		return p[0].Apply(t.root).Field("total_time_ms").AsDouble()
	}
	return 0.0
}

func (t threadTrace) firstPhaseTimeMs() float64 {
	p := slime.Find(t.root, hasTag("first_phase_profiling"))
	if len(p) == 1 {
		return p[0].Apply(t.root).Field("total_time_ms").AsDouble()
	}
	return 0.0
}

func (t threadTrace) secondPhaseTimeMs() float64 {
	p := slime.Find(t.root, hasTag("second_phase_profiling"))
	if len(p) == 1 {
		return p[0].Apply(t.root).Field("total_time_ms").AsDouble()
	}
	return 0.0
}

func (t threadTrace) profTimeMs() float64 {
	return t.matchTimeMs() + t.firstPhaseTimeMs() + t.secondPhaseTimeMs()
}

type protonTrace struct {
	root slime.Value
}

func (p protonTrace) findThreadTraces() []threadTrace {
	var traces []threadTrace
	slime.Select(p.root, hasTag("query_execution"), func(p *slime.Path, v slime.Value) {
		v.Field("threads").EachEntry(func(idx int, v slime.Value) {
			traces = append(traces, threadTrace{v})
		})
	})
	return traces
}

func (p protonTrace) extractQuery() *queryNode {
	query := slime.Invalid
	plan := slime.Find(p.root, hasTag("query_execution_plan"))
	if len(plan) == 1 {
		query = plan[0].Apply(p.root).Field("optimized")
	}
	return extractQueryNode(query)
}

type protonSummaryCtx struct {
	out *output
}

func (p *protonSummaryCtx) fmt(format string, args ...interface{}) {
	p.out.fmt(format, args...)
}

func (p *protonSummaryCtx) render(trace slime.Value) {
	if !trace.Valid() {
		return
	}
	if trace.Type() == slime.ARRAY {
		trace.EachEntry(func(_ int, value slime.Value) {
			p.render(value)
		})
	}
	tag := trace.Field("tag").AsString()
	ms := trace.Field("timestamp_ms").AsDouble()
	if event := trace.Field("event"); event.Valid() {
		p.out.fmt("%10.3f ms: %s\n", ms, event.AsString())
	}
	if tag == "query_setup" {
		p.render(trace.Field("traces"))
	}
	if tag == "query_execution" {
		p.out.fmt("%s%s\n", strings.Repeat(" ", 15), "(query execution happens here, analyzed below)")
	}
}

func (p protonTrace) renderSummary(out *output) {
	ctx := &protonSummaryCtx{out: out}
	ctx.render(p.root.Field("traces"))
}

func (p protonTrace) distributionKey() int64 {
	return p.root.Field("distribution-key").AsLong()
}

func (p protonTrace) documentType() string {
	return p.root.Field("document-type").AsString()
}

func (p protonTrace) durationMs() float64 {
	return p.root.Field("duration_ms").AsDouble()
}

func findProtonTraces(root slime.Value) []protonTrace {
	var traces []protonTrace
	slime.Select(root.Field("trace"), func(p *slime.Path, v slime.Value) bool {
		return slime.Valid(v.Field("distribution-key"), v.Field("document-type"), v.Field("duration_ms"))
	}, func(p *slime.Path, v slime.Value) {
		traces = append(traces, protonTrace{v})
	})
	return traces
}
