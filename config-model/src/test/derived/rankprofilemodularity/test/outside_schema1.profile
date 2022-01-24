rank-profile outside_schema1 {

    function fo1() {
        expression: now
    }

    first-phase {
        expression: fieldMatch(title).completeness
    }

}