package io.github.membertracker.infrastructure.dto;

import io.github.membertracker.domain.enumeration.MemberStatus;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;

/**
 * Body of POST /api/people/{id}/membership, optional as a whole. An absent {@code status} means MEMBER; only MEMBER
 * and INACTIVE are accepted (a new membership cannot start DECEASED, TRANSFERRED or ARCHIVED). An absent
 * {@code joinDate} means today.
 */
public class MembershipRequest {

    @PastOrPresent(message = "Join date cannot be in the future")
    private LocalDate joinDate;

    private MemberStatus status;

    public MembershipRequest() {
    }

    public LocalDate getJoinDate() {
        return joinDate;
    }

    public void setJoinDate(LocalDate joinDate) {
        this.joinDate = joinDate;
    }

    public MemberStatus getStatus() {
        return status;
    }

    public void setStatus(MemberStatus status) {
        this.status = status;
    }
}
