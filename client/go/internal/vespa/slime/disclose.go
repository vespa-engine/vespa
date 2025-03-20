package slime

type DataSink interface {
	Key(key string)
	Object()
	Array()
	End()
	Empty()
	Bool(v bool)
	Long(v int64)
	Double(v float64)
	String(v string)
	Data(v []byte)
}

func EmitEmpty(sink DataSink) {
	sink.Empty()
}

func EmitBool(sink DataSink, v bool) {
	sink.Bool(v)
}

func EmitLong(sink DataSink, v int64) {
	sink.Long(v)
}

func EmitDouble(sink DataSink, v float64) {
	sink.Double(v)
}

func EmitString(sink DataSink, v string) {
	sink.String(v)
}

func EmitData(sink DataSink, v []byte) {
	sink.Data(v)
}

func EmitArray(sink DataSink, inner func(sink DataSink)) {
	sink.Array()
	inner(sink)
	sink.End()
}

func EmitObject(sink DataSink, inner func(sink DataSink)) {
	sink.Object()
	inner(sink)
	sink.End()
}

func EmitNamedEmpty(sink DataSink, name string) {
	sink.Key(name)
	sink.Empty()
}

func EmitNamedBool(sink DataSink, name string, v bool) {
	sink.Key(name)
	sink.Bool(v)
}

func EmitNamedLong(sink DataSink, name string, v int64) {
	sink.Key(name)
	sink.Long(v)
}

func EmitNamedDouble(sink DataSink, name string, v float64) {
	sink.Key(name)
	sink.Double(v)
}

func EmitNamedString(sink DataSink, name string, v string) {
	sink.Key(name)
	sink.String(v)
}

func EmitNamedData(sink DataSink, name string, v []byte) {
	sink.Key(name)
	sink.Data(v)
}

func EmitNamedArray(sink DataSink, name string, inner func(sink DataSink)) {
	sink.Key(name)
	sink.Array()
	inner(sink)
	sink.End()
}

func EmitNamedObject(sink DataSink, name string, inner func(sink DataSink)) {
	sink.Key(name)
	sink.Object()
	inner(sink)
	sink.End()
}

type DataSource interface {
	Emit(sink DataSink)
}
