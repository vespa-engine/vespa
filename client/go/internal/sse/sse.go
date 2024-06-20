package sse

import (
	"bufio"
	"fmt"
	"io"
	"strings"
)

// Event represents a server-sent event. Name and ID are optional fields.
type Event struct {
	Name string
	ID   string
	Data string
}

// Decoder reads and decodes a server-sent event from an input stream.
type Decoder struct {
	scanner *bufio.Scanner
}

// Decode reads and decodes the next event from the underlying reader.
func (d *Decoder) Decode() (*Event, error) {
	// https://www.rfc-editor.org/rfc/rfc8895.html#name-server-push-server-sent-eve
	var (
		event    Event
		data     strings.Builder
		lastRead string
		gotName  bool
		gotID    bool
		gotData  bool
		decoding bool
	)
	for d.scanner.Scan() {
		line := strings.TrimSpace(d.scanner.Text())
		if line == "" {
			if decoding {
				break // Done with event
			} else {
				continue // Waiting for first non-empty line
			}
		}
		lastRead = line
		decoding = true
		parts := strings.SplitN(line, ": ", 2)
		if len(parts) < 2 || parts[0] == "" {
			continue
		}
		switch parts[0] {
		case "event":
			if gotName {
				return nil, fmt.Errorf("got more than one event line: last read %q", lastRead)
			}
			event.Name = parts[1]
			gotName = true
		case "id":
			if gotID {
				return nil, fmt.Errorf("got more than one id line: last read %q", lastRead)
			}
			event.ID = parts[1]
			gotID = true
		case "data":
			if data.Len() > 0 {
				data.WriteString(" ")
			}
			data.WriteString(parts[1])
			gotData = true
		default:
			return nil, fmt.Errorf("invalid field name %q: last read %q", parts[0], lastRead)
		}
	}
	if err := d.scanner.Err(); err != nil {
		return nil, err
	}
	if !decoding {
		return nil, io.EOF
	}
	if !event.IsEnd() && !gotData {
		return nil, fmt.Errorf("no data line found for event: last read %q", lastRead)
	}
	event.Data = data.String()
	return &event, nil
}

// NewDecoder creates a new Decoder that reads from r.
func NewDecoder(r io.Reader) *Decoder {
	return &Decoder{scanner: bufio.NewScanner(r)}
}

// NewDecoderSize creates a new Decoder that reads from r. The size argument specifies of the size of the buffer that
// decoder will use when decoding events. Size must be large enough to fit the largest expected event.
func NewDecoderSize(r io.Reader, size int) *Decoder {
	scanner := bufio.NewScanner(r)
	buf := make([]byte, 0, size)
	scanner.Buffer(buf, size)
	return &Decoder{scanner: scanner}
}

// IsEnd returns whether this event indicates that the stream has ended.
func (e Event) IsEnd() bool { return e.Name == "end" }

// String returns the string representation of event e.
func (e Event) String() string {
	var sb strings.Builder
	if e.Name != "" {
		sb.WriteString("event: ")
		sb.WriteString(e.Name)
		sb.WriteString("\n")
	}
	if e.ID != "" {
		sb.WriteString("id: ")
		sb.WriteString(e.ID)
		sb.WriteString("\n")
	}
	if e.Data != "" {
		sb.WriteString("data: ")
		sb.WriteString(e.Data)
		sb.WriteString("\n")
	}
	return sb.String()
}
