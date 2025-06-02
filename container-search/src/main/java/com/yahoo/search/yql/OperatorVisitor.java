// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.yql;

interface OperatorVisitor {

    <T extends Operator> boolean enter(OperatorNode<T> node);

    <T extends Operator> void exit(OperatorNode<T> node);

}
