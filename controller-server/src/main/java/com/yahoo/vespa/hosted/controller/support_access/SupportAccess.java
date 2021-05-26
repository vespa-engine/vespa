package com.yahoo.vespa.hosted.controller.support_access;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SupportAccess {

    public static final SupportAccess DISALLOWED_NO_HISTORY = new SupportAccess(List.of(), List.of());

    private final List<SupportAccessChange> changeHistory;
    private final List<SupportAccessGrant> grantHistory;

    /**
     * public for serializer - do not use
     */
    public SupportAccess(List<SupportAccessChange> changeHistory, List<SupportAccessGrant> grantHistory) {
        this.changeHistory = changeHistory;
        this.grantHistory = grantHistory;
    }

    public List<SupportAccessChange> changeHistory() {
        return changeHistory;
    }

    public List<SupportAccessGrant> grantHistory() {
        return grantHistory;
    }

    public CurrentStatus currentStatus(Instant now) {
        Optional<SupportAccessChange> allowingChange = changeHistory.stream().findFirst()
                .filter(change -> change.accessAllowedUntil().map(i -> i.isAfter(now)).orElse(false));

        return allowingChange
                .map(change -> new CurrentStatus(State.ALLOWED, change.accessAllowedUntil(), Optional.of(change.madeBy())))
                .orElseGet(() -> new CurrentStatus(State.NOT_ALLOWED, Optional.empty(), Optional.empty()));
    }

    public SupportAccess withAllowedUntil(Instant until, String changedBy, Instant changeTime) {
        if (!until.isAfter(changeTime))
            throw new IllegalArgumentException("Support access cannot be allowed for the past");

        verifyChangeOrdering(changeTime);
        return new SupportAccess(
                prepend(new SupportAccessChange(Optional.of(until), changeTime, changedBy), changeHistory),
                grantHistory);
    }

    public SupportAccess withDisallowed(String changedBy, Instant changeTime) {
        verifyChangeOrdering(changeTime);
        return new SupportAccess(
                prepend(new SupportAccessChange(Optional.empty(), changeTime, changedBy), changeHistory),
                grantHistory);
    }

    public SupportAccess withGrant(SupportAccessGrant supportAccessGrant) {
        return new SupportAccess(changeHistory, prepend(supportAccessGrant, grantHistory));
    }

    private void verifyChangeOrdering(Instant changeTime) {
        changeHistory.stream().findFirst().ifPresent(lastChange -> {
            if (changeTime.isBefore(lastChange.changeTime())) {
                throw new IllegalArgumentException("Support access change cannot be dated before previous change");
            }
        });
    }

    private <T> List<T> prepend(T newEntry, List<T> existingEntries) {
        return Stream.concat(Stream.of(newEntry), existingEntries.stream()) // latest change first
                .collect(Collectors.toUnmodifiableList());
    }

    public static class CurrentStatus {
        private final State state;
        private final Optional<Instant> allowedUntil;
        private final Optional<String> allowedBy;

        private CurrentStatus(State state, Optional<Instant> allowedUntil, Optional<String> allowedBy) {
            this.state = state;
            this.allowedUntil = allowedUntil;
            this.allowedBy = allowedBy;
        }

        public State state() {
            return state;
        }

        public Optional<Instant> allowedUntil() {
            return allowedUntil;
        }

        public Optional<String> allowedBy() {
            return allowedBy;
        }
    }

    public enum State {
        NOT_ALLOWED,
        ALLOWED
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SupportAccess that = (SupportAccess) o;
        return changeHistory.equals(that.changeHistory) && grantHistory.equals(that.grantHistory);
    }

    @Override
    public int hashCode() {
        return Objects.hash(changeHistory, grantHistory);
    }

    @Override
    public String toString() {
        return "SupportAccess{" +
                "changeHistory=" + changeHistory +
                ", grantHistory=" + grantHistory +
                '}';
    }
}
