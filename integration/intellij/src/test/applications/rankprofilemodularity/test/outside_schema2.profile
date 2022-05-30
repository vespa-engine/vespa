# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
rank-profile outside_schema2 {

    function fo2() {
        expression: random
    }

    first-phase {
        expression: fieldMatch(title).completeness
    }

}