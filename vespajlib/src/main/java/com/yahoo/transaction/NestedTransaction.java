// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A transaction which may contain a list of transactions, typically to represent a distributed transaction
 * over multiple systems.
 *
 * @author bratseth
 */
public final class NestedTransaction implements AutoCloseable {

    private static final Logger log = Logger.getLogger(NestedTransaction.class.getName());

    /** Nested transactions with ordering constraints, in the order they are added */
    private final List<ConstrainedTransaction> transactions = new ArrayList<>(2);

    /** A list of (non-transactional) operations to execute after this transaction has committed successfully */
    private final List<Runnable> onCommitted = new ArrayList<>(2);

    /**
     * Adds a transaction to this.
     *
     * @param  transaction the transaction to add
     * @param  before transaction classes which should commit after this, if present. It is beneficial
     *         to order transaction types from the least to most reliable. If conflicting ordering constraints are
     *         given this will not be detected at add time but the transaction will fail to commit
     * @return this for convenience
     */
    @SafeVarargs // don't warn on 'before' argument
    @SuppressWarnings("varargs") // don't warn on passing 'before' to the nested class constructor
    public final NestedTransaction add(Transaction transaction, Class<? extends Transaction> ... before) {
        transactions.add(new ConstrainedTransaction(transaction, before));
        return this;
    }

    /** Returns the transactions nested in this, as they will be committed. */
    public List<Transaction> transactions() { return organizeTransactions(transactions); }

    /** Perform a 2 phase commit */
    public void commit() {
        List<Transaction> organizedTransactions = organizeTransactions(transactions);

        // First phase
        for (Transaction transaction : organizedTransactions)
            transaction.prepare();

        // Second phase
        for (ListIterator<Transaction> i = organizedTransactions.listIterator(); i.hasNext(); ) {
            Transaction transaction = i.next();
            try {
                transaction.commit();
            }
            catch (Exception e) {
                // Clean up committed part or log that we can't
                i.previous();
                while (i.hasPrevious())
                    i.previous().rollbackOrLog();
                throw new IllegalStateException("Transaction failed during commit", e);
            }
        }

        // After commit: Execute completion tasks
        for (Runnable task : onCommitted) {
            try {
                task.run();
            }
            catch (Exception e) { // Don't throw from here as that indicates transaction didn't complete
                log.log(Level.WARNING, "A committed task in " + this + " caused an exception", e);
            }
        }
    }

    public void onCommitted(Runnable runnable) {
        onCommitted.add(runnable);
    }

    /** Free up any temporary resources held by this */
    @Override
    public void close() {
        for (ConstrainedTransaction transaction : transactions)
            transaction.transaction.close();
    }

    @Override
    public String toString() {
        return String.join(",", transactions.stream().map(Object::toString).collect(Collectors.toList()));
    }

    private List<Transaction> organizeTransactions(List<ConstrainedTransaction> transactions) {
        return orderTransactions(combineTransactions(transactions), findOrderingConstraints(transactions));
    }

    /** Combines all transactions of the same type to one */
    private List<Transaction> combineTransactions(List<ConstrainedTransaction> transactions) {
        List<Transaction> combinedTransactions = new ArrayList<>(transactions.size());
        for (List<Transaction> combinableTransactions :
                transactions.stream().map(ConstrainedTransaction::transaction).
                        collect(Collectors.groupingBy(Transaction::getClass)).values()) {
            Transaction combinedTransaction = combinableTransactions.get(0);
            for (int i = 1; i < combinableTransactions.size(); i++)
                combinedTransaction = combinedTransaction.add(combinableTransactions.get(i).operations());
            combinedTransactions.add(combinedTransaction);
        }
        return combinedTransactions;
    }

    private List<OrderingConstraint> findOrderingConstraints(List<ConstrainedTransaction> transactions) {
        List<OrderingConstraint> orderingConstraints = new ArrayList<>(1);
        for (ConstrainedTransaction transaction : transactions) {
            for (Class<? extends Transaction> afterThis : transaction.before())
                orderingConstraints.add(new OrderingConstraint(transaction.transaction().getClass(), afterThis));
        }
        return orderingConstraints;
    }

    /** Orders combined transactions consistent with the ordering constraints */
    private List<Transaction> orderTransactions(List<Transaction> transactions, List<OrderingConstraint> constraints) {
        if (transactions.size() == 1) return transactions;

        List<Transaction> orderedTransactions = new ArrayList<>();
        for (Transaction transaction : transactions)
            orderedTransactions.add(findSuitablePositionFor(transaction, orderedTransactions, constraints), transaction);
        return orderedTransactions;
    }

    private int findSuitablePositionFor(Transaction transaction, List<Transaction> orderedTransactions,
                                        List<OrderingConstraint> constraints) {
        for (int i = 0; i < orderedTransactions.size(); i++) {
            Transaction candidateNextTransaction = orderedTransactions.get(i);
            if ( ! mustBeAfter(candidateNextTransaction.getClass(), transaction.getClass(), constraints)) return i;

            // transaction must be after this: continue to next position
            if (mustBeAfter(transaction.getClass(), candidateNextTransaction.getClass(), constraints)) // must be after && must be before
                throw new IllegalStateException("Conflicting transaction ordering constraints between" +
                                                transaction + " and " + candidateNextTransaction);
        }
        return orderedTransactions.size(); // add last as it must be after everything
    }

    /**
     * Returns whether transaction type B must be after type A according to the ordering constraints.
     * This is the same as asking whether there is a path between node a and b in the bi-directional
     * graph defined by the ordering constraints.
     */
    private boolean mustBeAfter(Class<? extends Transaction> a, Class<? extends Transaction> b,
                                List<OrderingConstraint> constraints) {
        for (OrderingConstraint fromA : findAllOrderingConstraintsFrom(a, constraints)) {
            if (fromA.after().equals(b)) return true;
            if (mustBeAfter(fromA.after(), b, constraints)) return true;
        }
        return false;
    }

    private List<OrderingConstraint> findAllOrderingConstraintsFrom(Class<? extends Transaction> transactionType,
                                                                    List<OrderingConstraint> constraints) {
        return constraints.stream().filter(c -> c.before().equals(transactionType)).collect(Collectors.toList());
    }

    private static class ConstrainedTransaction {

        private final Transaction transaction;

        private final Class<? extends Transaction>[] before;

        public ConstrainedTransaction(Transaction transaction, Class<? extends Transaction>[] before) {
            this.transaction = transaction;
            this.before = before;
        }

        public Transaction transaction() { return transaction; }

        /** Returns transaction types which should commit after this */
        public Class<? extends Transaction>[] before() { return before; }

        @Override
        public String toString() {
            return transaction.toString();
        }

    }

    private static class OrderingConstraint {

        private final Class<? extends Transaction> before, after;

        public OrderingConstraint(Class<? extends Transaction> before, Class<? extends Transaction> after) {
            this.before = before;
            this.after = after;
        }

        public Class<? extends Transaction> before() { return before; }

        public Class<? extends Transaction> after() { return after; }

        @Override
        public String toString() { return before + " -> " + after; }

    }

}
