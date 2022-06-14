package com.yahoo.vespa.hosted.controller.tenant;

import com.yahoo.text.Text;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ArchiveAccess {

    private static final Pattern VALID_AWS_ARCHIVE_ACCESS_ROLE_PATTERN = Pattern.compile("arn:aws:iam::\\d{12}:.+");
    private static final Pattern VALID_GCP_ARCHIVE_ACCESS_MEMBER_PATTERN = Pattern.compile("(?<prefix>[a-zA-Z]+):.+");

    private static final Set<String> gcpMemberPrefixes = Set.of("user", "serviceAccount", "group", "domain");

    // AWS IAM Role
    private final Optional<String> awsRole;
    // GCP Member
    private final Optional<String> gcpMember;

    public ArchiveAccess() {
        this(Optional.empty(), Optional.empty());
    }

    private ArchiveAccess(Optional<String> awsRole, Optional<String> gcpMember) {
        this.awsRole = awsRole;
        this.gcpMember = gcpMember;

        awsRole.ifPresent(role -> validateAWSIAMRole(role));
        gcpMember.ifPresent(member -> validateGCPMember(member));
    }

    public ArchiveAccess withAWSRole(String role) {
        return new ArchiveAccess(Optional.of(role), gcpMember());
    }

    public ArchiveAccess withGCPMember(String member) {
        return new ArchiveAccess(awsRole(), Optional.of(member));
    }

    public ArchiveAccess withAWSRole(Optional<String> role) {
        return new ArchiveAccess(role, gcpMember());
    }

    public ArchiveAccess withGCPMember(Optional<String> member) {
        return new ArchiveAccess(awsRole(), member);
    }

    public ArchiveAccess removeAWSRole() {
        return new ArchiveAccess(Optional.empty(), gcpMember());
    }

    public ArchiveAccess removeGCPMember() {
        return new ArchiveAccess(awsRole(), Optional.empty());
    }

    private void validateAWSIAMRole(String role) {
        if (!VALID_AWS_ARCHIVE_ACCESS_ROLE_PATTERN.matcher(role).matches()) {
            throw new IllegalArgumentException(Text.format("Invalid archive access role '%s': Must match expected pattern: '%s'",
                    awsRole.get(), VALID_AWS_ARCHIVE_ACCESS_ROLE_PATTERN.pattern()));
        }
        if (role.length() > 100) {
            throw new IllegalArgumentException("Invalid archive access role too long, must be 100 or less characters");
        }
    }

    private void validateGCPMember(String member) {
        var matcher = VALID_GCP_ARCHIVE_ACCESS_MEMBER_PATTERN.matcher(member);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(Text.format("Invalid GCP archive access member '%s': Must match expected pattern: '%s'",
                    gcpMember.get(), VALID_GCP_ARCHIVE_ACCESS_MEMBER_PATTERN.pattern()));
        }
        var prefix = matcher.group("prefix");
        if (!gcpMemberPrefixes.contains(prefix)) {
            throw new IllegalArgumentException(Text.format("Invalid GCP member prefix '%s', must be one of '%s'",
                    prefix, gcpMemberPrefixes.stream().collect(Collectors.joining(", "))));
        }
        if (!"domain".equals(prefix) && !member.contains("@")) {
            throw new IllegalArgumentException(Text.format("Invalid GCP member '%s', prefix '%s' must be followed by an email id", member, prefix));
        }
    }

    public Optional<String> awsRole() {
        return awsRole;
    }

    public Optional<String> gcpMember() {
        return gcpMember;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArchiveAccess that = (ArchiveAccess) o;
        return awsRole.equals(that.awsRole) && gcpMember.equals(that.gcpMember);
    }

    @Override
    public int hashCode() {
        return Objects.hash(awsRole, gcpMember);
    }
}
