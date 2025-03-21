package tracedoctor

import (
	"bytes"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestTimelineImpact(t *testing.T) {
	tl := &timeline{}
	tl.add(10.0, "First event")
	tl.add(20.0, "Middle event")
	tl.add(30.0, "Last event")

	impact := tl.impact()

	assert.Equal(t, 20.0, impact, "Unexpected impact duration")
}

func TestTimelineDurationOf(t *testing.T) {
	tl := &timeline{}
	tl.add(10.0, "Start event")
	tl.add(30.0, "Middle event")
	tl.add(50.0, "End event")

	duration := tl.durationOf("Start event")
	assert.Equal(t, 20.0, duration, "Unexpected duration for 'Start event'")

	duration = tl.durationOf("Middle event")
	assert.Equal(t, 20.0, duration, "Unexpected duration for 'Middle event'")

	duration = tl.durationOf("End event")
	assert.Equal(t, 0.0, duration, "Expected duration for 'End event' to be 0 as there is no next entry")

	duration = tl.durationOf("Non-existent event")
	assert.Equal(t, 0.0, duration, "Expected duration for non-existent event to be 0")
}

func TestTimelineAdd(t *testing.T) {
	tl := &timeline{}
	tl.add(12.34, "Test Event")

	assert.Len(t, tl.list, 1, "Expected 1 entry in the timeline")
	assert.Equal(t, 12.34, tl.list[0].when, "Unexpected timestamp")
	assert.Equal(t, "Test Event", tl.list[0].what, "Unexpected event name")
}

func TestTimelineAddComment(t *testing.T) {
	tl := &timeline{}
	tl.addComment("This is a comment")

	assert.Len(t, tl.list, 1, "Expected 1 entry in the timeline")
	assert.Equal(t, -1.0, tl.list[0].when, "Unexpected timestamp for comment")
	assert.Equal(t, "This is a comment", tl.list[0].what, "Unexpected comment text")
}

func TestTimelineRender(t *testing.T) {
	var actual bytes.Buffer
	out := &output{out: &actual}

	tl := &timeline{}
	tl.add(1.23, "Start event")
	tl.addComment("This is a comment")
	tl.add(45.67, "End event")
	tl.render(out)

	var expected bytes.Buffer
	tab := newTable("timestamp", "event")
	tab.addRow("1.230 ms", "Start event")
	tab.addRow("", "This is a comment")
	tab.addRow("45.670 ms", "End event")
	tab.render(&output{out: &expected})

	assert.Equal(t, expected.String(), actual.String(), "Rendered output does not match expected")
}
