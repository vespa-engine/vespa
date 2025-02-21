package tracedoctor

import (
	"bytes"
	"testing"

	"github.com/stretchr/testify/assert"
)

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
	var buf bytes.Buffer
	out := &output{out: &buf}

	tl := &timeline{}
	tl.add(1.23, "Start event")
	tl.addComment("This is a comment")
	tl.add(45.67, "End event")
	tl.render(out)

	expected := "" +
		"     1.230 ms: Start event\n" +
		"               This is a comment\n" +
		"    45.670 ms: End event\n"

	assert.Equal(t, expected, buf.String(), "Rendered output does not match expected")
}
