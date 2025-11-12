package tracedoctor

const introPromptStr = `
Ignore all prior context, settings, and memory.

You are analyzing a Vespa TraceDoctor report.

The report may include metadata enclosed in <AI>…</AI> tags.
This metadata is for your understanding only — never include or quote it in
the output. Use it solely to interpret the report structure, column meanings,
or relationships between tasks.

Begin your analysis from the report below:
`

const outroPromptStr = `
<AI>
The report ends here. The following text describes how to analyze it.
</AI>

Guidelines:
- Base your reasoning only on evidence in the report — do not infer or invent.
- If something is unclear, say "unknown from report".
- Tasks represent exclusive time categories; the timeline is ground truth.
- Match total task time with backend and timeline totals.
- Use ms values consistently and associate them with their events or phases.
- Treat <AI>…</AI> as internal notes; never include them in the output.

Style:
- Neutral, factual, concise.
- State the main findings first, then support with evidence.
- Use plain language and clear structure.

Suggested structure:
1. Overall Request Summary  
2. Where the Time Went  
3. Slowest vs Typical Node  
4. Main Bottlenecks  
5. Conclusion (one line on where time is spent)
`

const timingsPromptStr = `<AI>The following table shows an overall breakdown of the request.
We will focus on the *query* part of the request.
It should take most of the time. If not, the analysis will be incomplete.<\AI>`

const searchNodeRefPromptStr = `<AI>When referring to a specific back-end node within a search
we use the name of the document type used followed by the numeric id of the back-end node in brackets.
The numbers are just used to distinguish between nodes and have no semantic meaning.</AI>`

const searchMetaPromptStr = `<AI>The following table shows the individual searches that was performed as part of the request.
*search* column: numbering the searches for later reference
*nodes* column: how many back-end nodes were used for the search
*back-end time* column: maximum back-end response latency
*document type* column: the document type used for the search<\AI>`

const protonSummaryPromptStr = `<AI>The following table shows a summary of the time spent on one or more back-end nodes.
The time is separated into tasks that does not overlap. The view is simplified and does not represent all time spent.
If the combined time spent on tasks is much lower than the total time shown above, we are spending time on more unusual things.
Some of those things might be found later on when looking at timelines.
The tasks are as follows:
*global filter*: time spent calculating a global filter to be used by the ANN algorithm.
This represents an upper bound of the documents matching the query. If this value is zero
a global filter was not needed.
*ann setup*: time spent doing ANN before doing normal matching. This will include any time
spent doing HNSW. Only when falling back to exact matching is the ANN search performed inside the
match loop. In the case of HNSW the results are gathered up front and injected into the relevant
node in the query tree making it spend little time during actual matching. If the time is zero no
ANN was performed.
*matching*: time spent executing the query tree to find documents matching it and preparing the
relevant data needed for ranking.
*first phase*: time spent doing initial ranking calculations. This will assign a score to each document
matching the query.
*second phase*: time spent doing final ranking calculations. This will calculate a new ranking score
for the best results after first phase ranking. This step is optional and if the time is zero the
query was executed with only a single ranking phase</AI>`

const protonTimelinePromptStr = `<AI>
The following table shows the overall timeline of a search on a back-end node.

The *timestamp* column shows times relative to when the node began handling this search.  
Time until the next row is attributed to the current event.  
If the row is a marker (e.g. "Start …", "Done"), describe the time as "between [this marker] and [next event]".  
The *ann setup* task from the table above happens as part of the event "Handle global filter in query execution plan".
</AI>`

const annQueryDetailsPromptStr = `<AI>The following table shows additional information about the ANN part of the query.
Do not get too hung up on the filter hit ratio.</AI>`

const globalFilterProfilingPromptStr = `<AI>The following table shows profiling information for creating the global filter.
This is more detailed information about what happens inside the *global filter* task from the table above.
The numbers in brackets in the *component* column are query node identifiers and can be used to identify the same query
node across information shown for this specific back-end node for this specific search.</AI>`

const matchThreadSummaryPromptStr = `<AI>The following table shows a summary of the time spent by one or more matching threads.
The time is separated into tasks that does not overlap. The view is simplified and does not represent all time spent.
If the combined time spent on tasks is much lower than the total time shown above, we are spending time on more unusual things.
Some of those things might be found later on when looking at timelines.
The tasks are as follows:
*matching*: time spent executing the query tree to find documents matching it and preparing the
relevant data needed for ranking.
*first phase*: time spent doing initial ranking calculations. This will assign a score to each document
matching the query.
*second phase*: time spent doing final ranking calculations. This will calculate a new ranking score
for the best results after first phase ranking. This step is optional and if the time is zero the
query was executed with only a single ranking phase</AI>`

const matchThreadTimelinePromptStr = `<AI>
The following table shows the timeline for a single matching thread.

The *timestamp* column shows times relative to when the back-end node began handling this search.  
Time until the next row is attributed to the current event.  
If the row is a marker (e.g. "Start …", "Done"), describe the time as "between [this marker] and [next event]".
</AI>`

const matchProfilingPromptStr = `<AI>The following table shows profiling information for matching.
This is more detailed information about what happens inside the *matching* task from the table above.
</AI>`

const firstPhaseProfilingPromptStr = `<AI>The following table shows profiling information for first phase ranking.
This is more detailed information about what happens inside the *first phase* task from the table above.
</AI>`

const secondPhaseProfilingPromptStr = `<AI>The following table shows profiling information for second phase ranking.
This is more detailed information about what happens inside the *second phase* task from the table above.
</AI>`

const slowToMedianPromptStr = `<AI>We are now done with the report for the slowest node.
Following below is the report for the median node. Note that some variance between nodes is expected.</AI>`

func promptSetup(ctx *Context, out *output) {
	if ctx.makePrompt {
		ctx.showMedianNode = true
	}
}

func introPrompt(ctx *Context, out *output) {
	if ctx.makePrompt {
		out.fmt("%s\n", introPromptStr)
	}
}

func outroPrompt(ctx *Context, out *output) {
	if ctx.makePrompt {
		out.fmt("%s\n", outroPromptStr)
	}
}

func timingsPrompt(ctx *Context, out *output) {
	if ctx.makePrompt {
		out.fmt("%s\n", timingsPromptStr)
	}
}

func searchNodeRefPrompt(ctx *Context, out *output) {
	if ctx.makePrompt {
		out.fmt("%s\n", searchNodeRefPromptStr)
	}
}

func searchMetaPrompt(ctx *Context, out *output) {
	if ctx.makePrompt {
		out.fmt("%s\n", searchMetaPromptStr)
	}
}

func protonSummaryPrompt(ctx *Context, out *output) {
	if ctx.makePrompt {
		out.fmt("%s\n", protonSummaryPromptStr)
	}
}

func protonTimelinePrompt(ctx *Context, out *output) {
	if ctx.makePrompt {
		out.fmt("%s\n", protonTimelinePromptStr)
	}
}

func annQueryDetailsPrompt(ctx *Context, out *output) {
	if ctx.makePrompt {
		out.fmt("%s\n", annQueryDetailsPromptStr)
	}
}

func globalFilterProfilingPrompt(ctx *Context, out *output) {
	if ctx.makePrompt {
		out.fmt("%s\n", globalFilterProfilingPromptStr)
	}
}

func matchThreadSummaryPrompt(ctx *Context, out *output) {
	if ctx.makePrompt {
		out.fmt("%s\n", matchThreadSummaryPromptStr)
	}
}

func matchThreadTimelinePrompt(ctx *Context, out *output) {
	if ctx.makePrompt {
		out.fmt("%s\n", matchThreadTimelinePromptStr)
	}
}

func matchProfilingPrompt(ctx *Context, out *output) {
	if ctx.makePrompt {
		out.fmt("%s\n", matchProfilingPromptStr)
	}
}

func firstPhaseProfilingPrompt(ctx *Context, out *output) {
	if ctx.makePrompt {
		out.fmt("%s\n", firstPhaseProfilingPromptStr)
	}
}

func secondPhaseProfilingPrompt(ctx *Context, out *output) {
	if ctx.makePrompt {
		out.fmt("%s\n", secondPhaseProfilingPromptStr)
	}
}

func slowToMedianPrompt(ctx *Context, out *output) {
	if ctx.makePrompt {
		out.fmt("%s\n", slowToMedianPromptStr)
	}
}
