package document

import (
	"bufio"
	"bytes"
	"fmt"
	"io"
	"math/rand"
	"strconv"
	"strings"
	"sync"

	"time"

	// Why do we use an experimental parser? This appears to be the only JSON library that satisfies the following
	// requirements:
	// - Faster than the std parser
	// - Supports parsing from a io.Reader
	// - Supports parsing token-by-token
	// - Few allocations during parsing (especially for large objects)
	"github.com/go-json-experiment/json"
)

type Operation int

const (
	OperationPut Operation = iota
	OperationUpdate
	OperationRemove

	jsonArrayStart  json.Kind = '['
	jsonArrayEnd    json.Kind = ']'
	jsonObjectStart json.Kind = '{'
	jsonObjectEnd   json.Kind = '}'
	jsonString      json.Kind = '"'
)

var (
	fieldsPrefix = []byte(`{"fields":`)
	fieldsSuffix = []byte("}")
)

// Id represents a Vespa document ID.
type Id struct {
	id string

	Type         string
	Namespace    string
	Number       *int64
	Group        string
	UserSpecific string
}

func (d Id) Equal(o Id) bool {
	return d.Type == o.Type &&
		d.Namespace == o.Namespace &&
		((d.Number == nil && o.Number == nil) || *d.Number == *o.Number) &&
		d.Group == o.Group &&
		d.UserSpecific == o.UserSpecific
}

func (d Id) String() string { return d.id }

// ParseId parses a serialized document ID string.
func ParseId(serialized string) (Id, error) {
	parts := strings.SplitN(serialized, ":", 4)
	if len(parts) < 4 || parts[0] != "id" {
		return Id{}, parseError(serialized)
	}
	namespace := parts[1]
	if namespace == "" {
		return Id{}, parseError(serialized)
	}
	docType := parts[2]
	if docType == "" {
		return Id{}, parseError(serialized)
	}
	rest := strings.SplitN(parts[3], ":", 2)
	if len(rest) < 2 {
		return Id{}, parseError(serialized)
	}
	options := rest[0]
	userSpecific := rest[1]
	var number *int64
	group := ""
	if strings.HasPrefix(options, "n=") {
		n, err := strconv.ParseInt(options[2:], 10, 64)
		if err != nil {
			return Id{}, parseError(serialized)
		}
		number = &n
	} else if strings.HasPrefix(options, "g=") {
		group = options[2:]
		if len(group) == 0 {
			return Id{}, parseError(serialized)
		}
	} else if options != "" {
		return Id{}, parseError(serialized)
	}
	if userSpecific == "" {
		return Id{}, parseError(serialized)
	}
	return Id{
		id:           serialized,
		Namespace:    namespace,
		Type:         docType,
		Number:       number,
		Group:        group,
		UserSpecific: userSpecific,
	}, nil
}

// Document represents a Vespa document operation.
type Document struct {
	Id        Id
	Condition string
	Body      []byte
	Operation Operation
	Create    bool

	resetFunc func()
}

func (d Document) Equal(o Document) bool {
	return d.Id.Equal(o.Id) &&
		d.Condition == o.Condition &&
		bytes.Equal(d.Body, o.Body) &&
		d.Operation == o.Operation &&
		d.Create == o.Create
}

// Reset discards the body of this document.
func (d *Document) Reset() {
	d.Body = nil
	if d.resetFunc != nil {
		d.resetFunc()
	}
}

// Decoder decodes documents from a JSON structure which is either an array of objects, or objects separated by newline.
type Decoder struct {
	dec *json.Decoder
	buf bytes.Buffer

	array bool
	jsonl bool

	fieldsEnd int64

	documentBuffers sync.Pool
}

func (d Document) String() string {
	var sb strings.Builder
	switch d.Operation {
	case OperationPut:
		sb.WriteString("put ")
	case OperationUpdate:
		sb.WriteString("update ")
	case OperationRemove:
		sb.WriteString("remove ")
	}
	sb.WriteString(d.Id.String())
	if d.Condition != "" {
		sb.WriteString(", condition=")
		sb.WriteString(d.Condition)
	}
	if d.Create {
		sb.WriteString(", create=true")
	}
	if d.Body != nil {
		sb.WriteString(", body=")
		sb.WriteString(string(d.Body))
	}
	return sb.String()
}

func (d *Decoder) guessMode() error {
	if d.array || d.jsonl {
		return nil
	}
	kind := d.dec.PeekKind()
	switch kind {
	case jsonArrayStart:
		if _, err := d.readNext(jsonArrayStart); err != nil {
			return err
		}
		d.array = true
	case jsonObjectStart:
		d.jsonl = true
	default:
		return fmt.Errorf("expected %s or %s, got %s", jsonArrayStart, jsonObjectStart, kind)
	}
	return nil
}

func (d *Decoder) readNext(kind json.Kind) (json.Token, error) {
	t, err := d.dec.ReadToken()
	if err != nil {
		return json.Token{}, err
	}
	if t.Kind() != kind {
		return json.Token{}, fmt.Errorf("unexpected json kind: %q: want %q", t, kind)
	}
	return t, nil
}

func (d *Decoder) readString() (string, error) {
	t, err := d.readNext(jsonString)
	if err != nil {
		return "", err
	}
	return t.String(), nil
}

func (d *Decoder) readBool() (bool, error) {
	t, err := d.dec.ReadToken()
	if err != nil {
		return false, err
	}
	kind := t.Kind()
	if kind != 't' && kind != 'f' {
		return false, fmt.Errorf("unexpected json kind: %q: want %q or %q", t, 't', 'f')
	}
	return t.Bool(), nil
}

func (d *Decoder) Decode() (Document, error) {
	doc, err := d.decode()
	if err != nil && err != io.EOF {
		return Document{}, fmt.Errorf("invalid json at byte offset %d: %w", d.dec.InputOffset(), err)
	}
	return doc, err
}

func (d *Decoder) buffer() *bytes.Buffer {
	buf := d.documentBuffers.Get().(*bytes.Buffer)
	buf.Reset()
	return buf
}

func (d *Decoder) readField(name string, offset int64, doc *Document) error {
	readId := false
	switch name {
	case "id", "put":
		readId = true
		doc.Operation = OperationPut
	case "update":
		readId = true
		doc.Operation = OperationUpdate
	case "remove":
		readId = true
		doc.Operation = OperationRemove
	case "condition":
		condition, err := d.readString()
		if err != nil {
			return err
		}
		doc.Condition = condition
	case "create":
		create, err := d.readBool()
		if err != nil {
			return err
		}
		doc.Create = create
	case "fields":
		if _, err := d.readNext(jsonObjectStart); err != nil {
			return err
		}
		// Skip data between start of operation and start of fields
		fieldsStart := d.dec.InputOffset() - 1
		d.buf.Next(int(fieldsStart - offset))
		depth := 1
		for depth > 0 {
			t, err := d.dec.ReadToken()
			if err != nil {
				return err
			}
			switch t.Kind() {
			case jsonObjectStart:
				depth++
			case jsonObjectEnd:
				depth--
			}
		}
		d.fieldsEnd = d.dec.InputOffset()
		fields := d.buf.Next(int(d.fieldsEnd - fieldsStart))
		// Try to re-use buffers holding the document body. The buffer is released by document.Reset()
		bodyBuf := d.buffer()
		bodyBuf.Grow(len(fieldsPrefix) + len(fields) + len(fieldsSuffix))
		bodyBuf.Write(fieldsPrefix)
		bodyBuf.Write(fields)
		bodyBuf.Write(fieldsSuffix)
		doc.Body = bodyBuf.Bytes()
		doc.resetFunc = func() { d.documentBuffers.Put(bodyBuf) }
	}
	if readId {
		s, err := d.readString()
		if err != nil {
			return err
		}
		id, err := ParseId(s)
		if err != nil {
			return err
		}
		doc.Id = id
	}
	return nil
}

func (d *Decoder) decode() (Document, error) {
	start := d.dec.InputOffset()
	if err := d.guessMode(); err != nil {
		return Document{}, err
	}
	if d.array && d.dec.PeekKind() == jsonArrayEnd {
		// Reached end of the array holding document operations
		if _, err := d.readNext(jsonArrayEnd); err != nil {
			return Document{}, err
		}
		return Document{}, io.EOF
	}
	// Start of document operation
	if _, err := d.readNext(jsonObjectStart); err != nil {
		return Document{}, err
	}
	var doc Document
loop:
	for {
		switch d.dec.PeekKind() {
		case jsonString:
			t, err := d.dec.ReadToken()
			if err != nil {
				return Document{}, err
			}
			if err := d.readField(t.String(), start, &doc); err != nil {
				return Document{}, err
			}
		default:
			if _, err := d.readNext(jsonObjectEnd); err != nil {
				return Document{}, err
			}
			// Drop operation from the buffer
			start = max(start, d.fieldsEnd)
			end := d.dec.InputOffset()
			d.buf.Next(int(end - start))
			break loop
		}
	}
	return doc, nil
}

func NewDecoder(r io.Reader) *Decoder {
	br := bufio.NewReaderSize(r, 1<<26)
	d := &Decoder{}
	d.documentBuffers.New = func() any { return &bytes.Buffer{} }
	d.dec = json.NewDecoder(io.TeeReader(br, &d.buf))
	return d
}

func parseError(value string) error {
	return fmt.Errorf("invalid document: expected id:<namespace>:<document-type>:[n=<number>|g=<group>]:<user-specific>, got %q", value)
}

// Generator is a reader that returns synthetic documents until a given deadline.
type Generator struct {
	Size     int
	Deadline time.Time

	buf     bytes.Buffer
	nowFunc func() time.Time
}

func NewGenerator(size int, deadline time.Time) *Generator {
	return &Generator{Size: size, Deadline: deadline, nowFunc: time.Now}
}

func randString(size int) string {
	b := make([]byte, size)
	for i := range b {
		b[i] = byte('a' + rand.Intn('z'-'a'+1))
	}
	return string(b)
}

func (g *Generator) Read(p []byte) (int, error) {
	if g.buf.Len() == 0 {
		if !g.nowFunc().Before(g.Deadline) {
			return 0, io.EOF
		}
		fmt.Fprintf(&g.buf, "{\"put\": \"id:test:test::%s\", \"fields\": {\"test\": \"%s\"}}\n", randString(8), randString(g.Size))
	}
	return g.buf.Read(p)
}

type number interface{ float64 | int64 | int }

func min[T number](x, y T) T {
	if x < y {
		return x
	}
	return y
}

func max[T number](x, y T) T {
	if x > y {
		return x
	}
	return y
}
