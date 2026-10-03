package io.github.membertracker.infrastructure;

import io.github.membertracker.domain.model.Member;
import io.github.membertracker.infrastructure.dto.ExportMembersRequest;
import io.github.membertracker.infrastructure.dto.MemberRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.github.membertracker.usecase.*;
import io.github.membertracker.utils.CsvUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/members")
@Validated
@Tag(name = "Members", description = "Church members: list, edit, overdue and CSV export.")
public class MemberController {

    private final GetAllMembersUseCase getAllMembersUseCase;
    private final GetMemberByIdUseCase getMemberByIdUseCase;
    private final GetActiveMembersUseCase getActiveMembersUseCase;
    private final GetInactiveMembersUseCase getInactiveMembersUseCase;
    private final SaveMemberUseCase saveMemberUseCase;
    private final UpdateMemberUseCase updateMemberUseCase;
    private final DeleteMemberUseCase deleteMemberUseCase;
    private final GetMembersWithMissedPaymentsUseCase getMembersWithMissedPaymentsUseCase;

    @Autowired
    public MemberController(GetAllMembersUseCase getAllMembersUseCase,
                           GetMemberByIdUseCase getMemberByIdUseCase,
                           GetActiveMembersUseCase getActiveMembersUseCase,
                           GetInactiveMembersUseCase getInactiveMembersUseCase,
                           SaveMemberUseCase saveMemberUseCase,
                           UpdateMemberUseCase updateMemberUseCase,
                           DeleteMemberUseCase deleteMemberUseCase,
                           GetMembersWithMissedPaymentsUseCase getMembersWithMissedPaymentsUseCase) {
        this.getAllMembersUseCase = getAllMembersUseCase;
        this.getMemberByIdUseCase = getMemberByIdUseCase;
        this.getActiveMembersUseCase = getActiveMembersUseCase;
        this.getInactiveMembersUseCase = getInactiveMembersUseCase;
        this.saveMemberUseCase = saveMemberUseCase;
        this.updateMemberUseCase = updateMemberUseCase;
        this.deleteMemberUseCase = deleteMemberUseCase;
        this.getMembersWithMissedPaymentsUseCase = getMembersWithMissedPaymentsUseCase;
    }

    @GetMapping
    @PreAuthorize("hasRole('VOLUNTEER')")
    @Operation(summary = "List members (VOLUNTEER+)")
    public List<Member> getAllMembers() {
        return getAllMembersUseCase.invoke();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('VOLUNTEER')")
    @Operation(summary = "Get a member by id (VOLUNTEER+)")
    public ResponseEntity<Member> getMemberById(@PathVariable @Positive Long id) {
        return getMemberByIdUseCase.invoke(id)
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
        return saveMemberUseCase.invoke(request.getName(), request.getEmail(), request.getPhone(), request.getJoinDate());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "Update a member (STAFF+)")
    public ResponseEntity<Member> updateMember(@PathVariable @Positive Long id, @Valid @RequestBody MemberRequest request) {
        return updateMemberUseCase.invoke(id, request.getName(), request.getEmail(), request.getPhone(),
                        request.getJoinDate(), request.getActive())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a member (ADMIN)")
    public ResponseEntity<Void> deleteMember(@PathVariable @Positive Long id) {
        if (getMemberByIdUseCase.invoke(id).isPresent()) {
            deleteMemberUseCase.invoke(id);
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/overdue/{months}")
    @PreAuthorize("hasRole('VOLUNTEER')")
    @Operation(summary = "List members who missed payments for the given number of months (VOLUNTEER+)")
    public List<Member> getMembersWithOverduePayments(@PathVariable @Min(1) int months) {
        return getMembersWithMissedPaymentsUseCase.invoke(months);
    }

    @GetMapping("/export")
    @PreAuthorize("hasRole('VOLUNTEER')")
    @Operation(summary = "Export all members as CSV (VOLUNTEER+)")
    public ResponseEntity<StreamingResponseBody> exportMembers() {
        return csvResponse(getAllMembersUseCase.invoke());
    }

    @PostMapping("/export")
    @PreAuthorize("hasRole('VOLUNTEER')")
    @Operation(summary = "Export selected members as CSV (VOLUNTEER+)")
    public ResponseEntity<StreamingResponseBody> exportSelectedMembers(@Valid @RequestBody ExportMembersRequest request) {
        Set<Long> ids = new HashSet<>(request.getIds());
        List<Member> selected = getAllMembersUseCase.invoke().stream()
                .filter(member -> ids.contains(member.getId()))
                .toList();
        return csvResponse(selected);
    }

    private ResponseEntity<StreamingResponseBody> csvResponse(List<Member> members) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE;
        
        StreamingResponseBody stream = out -> {
            PrintWriter writer = new PrintWriter(out);
            try {
                // CSV Header
                writer.println("id,name,email,phone,joinDate,active,consecutiveMonthsMissed");
                
                // CSV Data
                for (Member member : members) {
                    writer.printf("%s,%s,%s,%s,%s,%s,%d%n",
                        member.getId() != null ? member.getId() : "0",
                        CsvUtils.escapeCsv(member.getName()),
                        CsvUtils.escapeCsv(member.getEmail()),
                        CsvUtils.escapeCsv(member.getPhone()),
                        member.getJoinDate() != null ? member.getJoinDate().format(dateFormatter) : "",
                        member.isActive(),
                        member.getConsecutiveMonthsMissed()
                    );
                }
            } catch (Exception e) {
                System.err.println("Error exporting members: " + e.getMessage());
                throw new RuntimeException("Error exporting members", e);
            } finally {
                writer.flush();
            }
        };
        
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=members.csv")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(stream);
    }
}
