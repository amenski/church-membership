package io.github.membertracker.infrastructure.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public class ExportMembersRequest {

    @NotEmpty(message = "At least one member id is required")
    @Size(max = 5000, message = "At most 5000 members can be exported at once")
    private List<@Positive(message = "Member ids must be positive") Long> ids;

    public ExportMembersRequest() {}

    public List<Long> getIds() {
        return ids;
    }

    public void setIds(List<Long> ids) {
        this.ids = ids;
    }
}
