package io.github.membertracker.infrastructure;

import io.github.membertracker.domain.enumeration.MemberStatus;
import io.github.membertracker.domain.model.Person;
import io.github.membertracker.infrastructure.dto.MembershipRequest;
import io.github.membertracker.infrastructure.dto.PersonRequest;
import io.github.membertracker.infrastructure.dto.PersonResponse;
import io.github.membertracker.infrastructure.security.ArchivedVisibility;
import io.github.membertracker.usecase.CreatePersonUseCase;
import io.github.membertracker.usecase.DeletePersonUseCase;
import io.github.membertracker.usecase.GetPeopleUseCase;
import io.github.membertracker.usecase.GetPersonByIdUseCase;
import io.github.membertracker.usecase.StartMembershipUseCase;
import io.github.membertracker.usecase.UpdatePersonUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

import java.util.List;
import java.util.Optional;

/**
 * People on the register, including those with no membership (children, dependents). Dues, reminders, messages,
 * payments and the members export all stay on member rows; a person here appears in none of them until a membership
 * is started.
 */
@RestController
@RequestMapping("/api/people")
@Validated
@Tag(name = "People", description = "Everyone on the register, members and dependents (people without a membership).")
public class PeopleController {

    private final GetPeopleUseCase getPeopleUseCase;
    private final GetPersonByIdUseCase getPersonByIdUseCase;
    private final CreatePersonUseCase createPersonUseCase;
    private final UpdatePersonUseCase updatePersonUseCase;
    private final DeletePersonUseCase deletePersonUseCase;
    private final StartMembershipUseCase startMembershipUseCase;

    public PeopleController(GetPeopleUseCase getPeopleUseCase, GetPersonByIdUseCase getPersonByIdUseCase,
                            CreatePersonUseCase createPersonUseCase, UpdatePersonUseCase updatePersonUseCase,
                            DeletePersonUseCase deletePersonUseCase, StartMembershipUseCase startMembershipUseCase) {
        this.getPeopleUseCase = getPeopleUseCase;
        this.getPersonByIdUseCase = getPersonByIdUseCase;
        this.createPersonUseCase = createPersonUseCase;
        this.updatePersonUseCase = updatePersonUseCase;
        this.deletePersonUseCase = deletePersonUseCase;
        this.startMembershipUseCase = startMembershipUseCase;
    }

    @GetMapping
    @PreAuthorize("hasRole('VOLUNTEER')")
    @Operation(summary = "List people; withoutMembership=true lists only the dependents (VOLUNTEER+)")
    public List<PersonResponse> getPeople(@RequestParam(defaultValue = "false") boolean withoutMembership) {
        return getPeopleUseCase.invoke(withoutMembership).stream()
                .map(PersonResponse::of)
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('VOLUNTEER')")
    @Operation(summary = "Get a person by id; an archived member is hidden from everybody but an ADMIN (VOLUNTEER+)")
    public ResponseEntity<PersonResponse> getPersonById(@PathVariable @Positive Long id) {
        return getPersonByIdUseCase.invoke(id)
                .filter(PeopleController::visible)
                .map(PersonResponse::of)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "Create a person with no membership (STAFF+)")
    public PersonResponse createPerson(@Valid @RequestBody PersonRequest request) {
        return PersonResponse.of(createPersonUseCase.invoke(request.getName(), request.getEmail(),
                request.getPhone(), request.getBirthDate(), request.getHouseholdId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "Update a person; a person with a membership keeps the member rows in step (STAFF+)")
    public ResponseEntity<PersonResponse> updatePerson(@PathVariable @Positive Long id,
                                                       @Valid @RequestBody PersonRequest request) {
        // Check first, like the member endpoint: a non-admin must not edit an archived person and then get a 404.
        if (getPersonByIdUseCase.invoke(id).filter(PeopleController::visible).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Optional<Person> updated = updatePersonUseCase.invoke(id, request.getName(), request.getEmail(),
                request.getPhone(), request.getBirthDate(), request.isHouseholdIdSet(), request.getHouseholdId());
        return updated
                .map(PersonResponse::of)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a person with no membership; 409 while they have one (ADMIN)")
    public ResponseEntity<Void> deletePerson(@PathVariable @Positive Long id) {
        return deletePersonUseCase.invoke(id)
                ? ResponseEntity.ok().build()
                : ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/membership")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "Turn a person into a member (STAFF+); 409 when they already have a membership")
    public ResponseEntity<PersonResponse> startMembership(@PathVariable @Positive Long id,
                                                          @RequestBody(required = false) @Valid MembershipRequest request) {
        MembershipRequest body = request == null ? new MembershipRequest() : request;
        return startMembershipUseCase.invoke(id, body.getJoinDate(), body.getStatus())
                .map(PersonResponse::of)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** An archived member is "not found" for everybody but an ADMIN, exactly as on the member endpoints. */
    private static boolean visible(Person person) {
        return person.memberStatus() != MemberStatus.ARCHIVED || ArchivedVisibility.canSeeArchived();
    }
}
