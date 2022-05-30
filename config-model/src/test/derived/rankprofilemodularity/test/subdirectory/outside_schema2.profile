rank-profile outside_schema2 {

    function fo2() {
        expression: random
    }

    first-phase {
        expression: fieldMatch(title).completeness
    }

}