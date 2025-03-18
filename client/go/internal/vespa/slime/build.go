package slime

type Builder struct {
	root  Value
	stack []Value
	key   string
}

func (b *Builder) Result() Value {
	if b.root == nil {
		return Empty
	}
	return b.root
}

func (b *Builder) addValue(value Value) Value {
	if len(b.stack) == 0 {
		b.root = value
		return value
	} else {
		parent := b.stack[len(b.stack)-1]
		if parent.Type() == OBJECT {
			return parent.Set(b.key, value)
		} else {
			return parent.Add(value)
		}
	}
}

func (b *Builder) Key(key string) {
	b.key = key
}

func (b *Builder) Object() {
	b.stack = append(b.stack, b.addValue(Object()))
}

func (b *Builder) Array() {
	b.stack = append(b.stack, b.addValue(Array()))
}

func (b *Builder) End() {
	if len(b.stack) > 0 {
		b.stack = b.stack[:len(b.stack)-1]
	}
}

func (b *Builder) Empty() {
	b.addValue(Empty)
}

func (b *Builder) Bool(v bool) {
	b.addValue(Bool(v))
}

func (b *Builder) Long(v int64) {
	b.addValue(Long(v))
}

func (b *Builder) Double(v float64) {
	b.addValue(Double(v))
}

func (b *Builder) String(v string) {
	b.addValue(String(v))
}

func (b *Builder) Data(v []byte) {
	b.addValue(Data(v))
}
