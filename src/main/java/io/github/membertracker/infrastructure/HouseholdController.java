package io.github.membertracker.infrastructure;

import io.github.membertracker.domain.model.HouseholdDetails;
import io.github.membertracker.infrastructure.dto.HouseholdRequest;
import io.github.membertracker.infrastructure.dto.HouseholdResponse;
import io.github.membertracker.infrastructure.dto.HouseholdSummaryResponse;
import io.github.membertracker.infrastructure.security.ArchivedVisibility;
import io.github.membertracker.usecase.CreateHouseholdUseCase;
import io.github.membertracker.usecase.DeleteHouseholdUseCase;
import io.github.membertracker.usecase.GetAllHouseholdsUseCase;
import io.github.membertracker.usecase.GetHouseholdByIdUseCase;
import io.github.membertracker.usecase.UpdateHouseholdUseCase;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/households")
@Validated
@Tag(name = "Households", description = "Families and shared addresses: name, address, notes and the members in them.")
public class HouseholdController {

    private final GetAllHouseholdsUseCase getAllHouseholdsUseCase;
    private final GetHouseholdByIdUseCase getHouseholdByIdUseCase;
    private final CreateHouseholdUseCase createHouseholdUseCase;
    private final UpdateHouseholdUseCase updateHouseholdUseCase;
    private final DeleteHouseholdUseCase deleteHouseholdUseCase;

    public HouseholdController(GetAllHouseholdsUseCase getAllHouseholdsUseCase,
                               GetHouseholdByIdUseCase getHouseholdByIdUseCase,
                               CreateHouseholdUseCase createHouseholdUseCase,
                               UpdateHouseholdUseCase updateHouseholdUseCase,
                               DeleteHouseholdUseCase deleteHouseholdUseCase) {
        this.getAllHouseholdsUseCase = getAllHouseholdsUseCase;
        this.getHouseholdByIdUseCase = getHouseholdByIdUseCase;
        this.createHouseholdUseCase = createHouseholdUseCase;
        this.updateHouseholdUseCase = updateHouseholdUseCase;
        this.deleteHouseholdUseCase = deleteHouseholdUseCase;
    }

    @GetMapping
    @PreAuthorize("hasRole('VOLUNTEER')")
    @Operation(summary = "List households with their member counts (VOLUNTEER+)")
    public List<HouseholdSummaryResponse> getAllHouseholds() {
        boolean canSeeArchived = ArchivedVisibility.canSeeArchived();
        return getAllHouseholdsUseCase.invoke().stream()
                .map(summary -> HouseholdSummaryResponse.of(summary, canSeeArchived))
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('VOLUNTEER')")
    @Operation(summary = "Get a household with its members; archived members only for an ADMIN (VOLUNTEER+)")
    public ResponseEntity<HouseholdResponse> getHouseholdById(@PathVariable @Positive Long id) {
        return respond(getHouseholdByIdUseCase.invoke(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "Create a household (STAFF+)")
    public HouseholdResponse createHousehold(@Valid @RequestBody HouseholdRequest request) {
        HouseholdDetails created = createHouseholdUseCase.invoke(request.getName(), request.getAddressLine1(),
                request.getAddressLine2(), request.getCity(), request.getPostalCode(), request.getNotes());
        return HouseholdResponse.of(created, ArchivedVisibility.canSeeArchived());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "Update a household's name, address and notes (STAFF+)")
    public ResponseEntity<HouseholdResponse> updateHousehold(@PathVariable @Positive Long id,
                                                             @Valid @RequestBody HouseholdRequest request) {
        return respond(updateHouseholdUseCase.invoke(id, request.getName(), request.getAddressLine1(),
                request.getAddressLine2(), request.getCity(), request.getPostalCode(), request.getNotes()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a household nobody belongs to; 409 while people are assigned to it (ADMIN)")
    public ResponseEntity<Void> deleteHousehold(@PathVariable @Positive Long id) {
        return deleteHouseholdUseCase.invoke(id)
                ? ResponseEntity.ok().build()
                : ResponseEntity.notFound().build();
    }

    private static ResponseEntity<HouseholdResponse> respond(Optional<HouseholdDetails> details) {
        boolean canSeeArchived = ArchivedVisibility.canSeeArchived();
        return details.map(d -> ResponseEntity.ok(HouseholdResponse.of(d, canSeeArchived)))
                .orElse(ResponseEntity.notFound().build());
    }
}
