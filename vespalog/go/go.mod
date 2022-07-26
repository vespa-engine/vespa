module github.com/vespa-engine/vespa/vespalog/go

go 1.16

require (
	github.com/spf13/cobra v1.4.0
	github.com/vespa-engine/vespa/client/go v0.0.0-00010101000000-000000000000
)

replace github.com/vespa-engine/vespa/client/go => ../../client/go
