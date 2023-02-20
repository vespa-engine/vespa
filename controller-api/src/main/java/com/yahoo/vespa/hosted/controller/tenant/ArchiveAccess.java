package com.yahoo.vespa.hosted.controller.tenant;

import com.amazonaws.arn.Arn;
import com.yahoo.text.Text;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ArchiveAccess {

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

    private static final Pattern ACCOUNT_ID_PATTERN = Pattern.compile("\\d{12}");
    private static void validateAWSIAMRole(String role) {
        if (role.length() > 100) {
            throw new IllegalArgumentException("Invalid archive access role too long, must be 100 or less characters");
        }
        try {
            var arn = Arn.fromString(role);
            if (!arn.getPartition().equals("aws")) throw new IllegalArgumentException("Partition must be 'aws'");
            if (!arn.getService().equals("iam")) throw new IllegalArgumentException("Service must be 'iam'");
            var resourceType = arn.getResource().getResourceType();
            if (resourceType == null) throw new IllegalArgumentException("Missing resource type - must be 'role' or 'user'");
            if (!List.of("user", "role").contains(resourceType))
                throw new IllegalArgumentException("Invalid resource type - must be either a 'role' or 'user'");
            var accountId = arn.getAccountId();
            if (!ACCOUNT_ID_PATTERN.matcher(accountId).matches())
                throw new IllegalArgumentException("Account id must be a 12-digit number");
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(Text.format("Invalid archive access IAM role '%s': %s", role, e.getMessage()));
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
