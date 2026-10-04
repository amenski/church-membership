package io.github.membertracker.infrastructure;

import io.github.membertracker.domain.enumeration.ActivityType;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.infrastructure.dto.ExportMembersRequest;
import io.github.membertracker.infrastructure.dto.MemberRequest;
import io.github.membertracker.infrastructure.security.ArchivedVisibility;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.github.membertracker.usecase.*;
import io.github.membertracker.utils.CsvUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/members")
@Validated
@Tag(name = "Members", description = "Church members: list, edit, overdue and CSV export.")
public class MemberController {

    private final GetAllMembersUseCase getAllMembersUseCase;
    private final GetMemberByIdUseCase getMemberByIdUseCase;
    private final GetActiveMembersUseCase getActiveMembersUseCase;
    private final GetInactiveMembersUseCase getInactiveMembersUseCase;
    private final GetArchivedMembersUseCase getArchivedMembersUseCase;
    private final SaveMemberUseCase saveMemberUseCase;
    private final UpdateMemberUseCase updateMemberUseCase;
    private final ArchiveMemberUseCase archiveMemberUseCase;
    private final DeleteMemberPermanentlyUseCase deleteMemberPermanentlyUseCase;
    private final GetMembersWithMissedPaymentsUseCase getMembersWithMissedPaymentsUseCase;
    private final RecordActivityUseCase recordActivityUseCase;

    @Autowired
    public MemberController(GetAllMembersUseCase getAllMembersUseCase,
                           GetMemberByIdUseCase getMemberByIdUseCase,
                           GetActiveMembersUseCase getActiveMembersUseCase,
                           GetInactiveMembersUseCase getInactiveMembersUseCase,
                           GetArchivedMembersUseCase getArchivedMembersUseCase,
                           SaveMemberUseCase saveMemberUseCase,
                           UpdateMemberUseCase updateMemberUseCase,
                           ArchiveMemberUseCase archiveMemberUseCase,
                           DeleteMemberPermanentlyUseCase deleteMemberPermanentlyUseCase,
                           GetMembersWithMissedPaymentsUseCase getMembersWithMissedPaymentsUseCase,
                           RecordActivityUseCase recordActivityUseCase) {
        this.getAllMembersUseCase = getAllMembersUseCase;
        this.getMemberByIdUseCase = getMemberByIdUseCase;
        this.getActiveMembersUseCase = getActiveMembersUseCase;
        this.getInactiveMembersUseCase = getInactiveMembersUseCase;
        this.getArchivedMembersUseCase = getArchivedMembersUseCase;
        this.saveMemberUseCase = saveMemberUseCase;
        this.updateMemberUseCase = updateMemberUseCase;
        this.archiveMemberUseCase = archiveMemberUseCase;
        this.deleteMemberPermanentlyUseCase = deleteMemberPermanentlyUseCase;
        this.getMembersWithMissedPaymentsUseCase = getMembersWithMissedPaymentsUseCase;
        this.recordActivityUseCase = recordActivityUseCase;
    }

    @GetMapping
    @PreAuthorize("hasRole('VOLUNTEER') and (!#archived or hasRole('ADMIN'))")
    @Operation(summary = "List members, archived ones hidden; archived=true lists only the archived (VOLUNTEER+, ADMIN for archived)")
    public List<Member> getAllMembers(@RequestParam(defaultValue = "false") boolean archived) {
        return archived ? getArchivedMembersUseCase.invoke() : getAllMembersUseCase.invoke();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('VOLUNTEER')")
    @Operation(summary = "Get a member by id (VOLUNTEER+)")
    public ResponseEntity<Member> getMemberById(@PathVariable @Positive Long id) {
        return ArchivedVisibility.visible(getMemberByIdUseCase.invoke(id))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/active")
    @PreAuthorize("hasRole('VOLUNTEER')")
    @Operation(summary = "List active members (VOLUNTEER+)")
    public List<Member> getActiveMembers() {
        return getActiveMembersUseCase.invoke();
    }

    @GetMapping("/inactive")
    @PreAuthorize("hasRole('VOLUNTEER')")
    @Operation(summary = "List inactive members (VOLUNTEER+)")
    public List<Member> getInactiveMembers() {
        return getInactiveMembersUseCase.invoke();
    }

    @PostMapping
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "Create a member (STAFF+)")
    public Member createMember(@Valid @RequestBody MemberRequest request) {
        if (request.getHouseholdId() != null) {
            return saveMemberUseCase.invoke(request.getName(), request.getEmail(), request.getPhone(),
                    request.getJoinDate(), request.statusForCreate(), request.getHouseholdId());
        }
        return saveMemberUseCase.invoke(request.getName(), request.getEmail(), request.getPhone(), request.getJoinDate(),
                request.statusForCreate());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "Update a member; restoring an archived member is ADMIN only (STAFF+)")
    public ResponseEntity<Member> updateMember(@PathVariable @Positive Long id, @Valid @RequestBody MemberRequest request) {
        if (ArchivedVisibility.visible(getMemberByIdUseCase.invoke(id)).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Optional<Member> updated = request.isHouseholdIdSet()
                ? updateMemberUseCase.invoke(id, request.getName(), request.getEmail(), request.getPhone(),
                        request.getJoinDate(), request.getStatus(), request.getActive(), true, request.getHouseholdId())
                : updateMemberUseCase.invoke(id, request.getName(), request.getEmail(), request.getPhone(),
                        request.getJoinDate(), request.getStatus(), request.getActive());
        return updated
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Archive a member: hidden from the lists, payments and messages kept (ADMIN)")
    public ResponseEntity<Void> archiveMember(@PathVariable @Positive Long id) {
        return archiveMemberUseCase.invoke(id).isPresent()
                ? ResponseEntity.ok().build()
                : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}/permanent")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a member for good; 409 when they have payments or messages (ADMIN)")
    public ResponseEntity<Void> deleteMemberPermanently(@PathVariable @Positive Long id) {
        return deleteMemberPermanentlyUseCase.invoke(id)
                ? ResponseEntity.ok().build()
                : ResponseEntity.notFound().build();
    }

    @GetMapping("/overdue/{months}")
    @PreAuthorize("hasRole('VOLUNTEER')")
    @Operation(summary = "List members who missed payments for the given number of months (VOLUNTEER+)")
    public List<Member> getMembersWithOverduePayments(@PathVariable @Min(1) int months) {
        return getMembersWithMissedPaymentsUseCase.invoke(months);
    }

    @GetMapping("/export")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "Export all members except the archived as CSV (STAFF+)")
    public ResponseEntity<byte[]> exportMembers() {
        return exportResponse(getAllMembersUseCase.invoke());
    }

    @PostMapping("/export")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "Export the members with the given ids as CSV; archived members only for an ADMIN (STAFF+)")
    public ResponseEntity<byte[]> exportSelectedMembers(@Valid @RequestBody ExportMembersRequest request) {
        List<Member> selected = new HashSet<>(request.getIds()).stream()
                .sorted()
                .map(id -> ArchivedVisibility.visible(getMemberByIdUseCase.invoke(id)))
                .flatMap(Optional::stream)
                .toList();
        return exportResponse(selected);
    }

    private ResponseEntity<byte[]> exportResponse(List<Member> members) {
        ResponseEntity<byte[]> response = csvResponse(members);
        recordActivityUseCase.record(ActivityType.MEMBERS_EXPORTED, "Exported " + members.size()
                + (members.size() == 1 ? " member" : " members"), "MEMBER", null);
        return response;
    }

    private ResponseEntity<byte[]> csvResponse(List<Member> members) {
        StringBuilder csv = new StringBuilder("id,name,email,phone,joinDate,active,consecutiveMonthsMissed,status\n");
        for (Member member : members) {
            csv.append(String.format("%s,%s,%s,%s,%s,%s,%d,%s%n",
                member.getId() != null ? member.getId() : "0",
                CsvUtils.escapeCsv(member.getName()),
                CsvUtils.escapeCsv(member.getEmail()),
                CsvUtils.escapeCsv(member.getPhone()),
                member.getJoinDate() != null ? member.getJoinDate().format(DateTimeFormatter.ISO_LOCAL_DATE) : "",
                member.isActive(),
                member.getConsecutiveMonthsMissed(),
                member.getStatus()));
        }
        return CsvUtils.attachment("members.csv", csv.toString());
    }
}
