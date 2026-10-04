package io.github.membertracker.infrastructure;

import io.github.membertracker.usecase.GetMyDuesUseCase;
import io.github.membertracker.usecase.GetMyDuesUseCase.MyDues;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@Tag(name = "My dues", description = "The signed-in user's own membership, matched to a member by email.")
public class MyDuesController {

    private final GetMyDuesUseCase getMyDuesUseCase;

    public MyDuesController(GetMyDuesUseCase getMyDuesUseCase) {
        this.getMyDuesUseCase = getMyDuesUseCase;
    }

    @GetMapping("/dues")
    @PreAuthorize("hasRole('MEMBER')")
    @Operation(summary = "Own dues (any signed-in user); 404 when no single member has the signed-in email")
    public ResponseEntity<MyDues> getMyDues(Authentication authentication) {
        return getMyDuesUseCase.invoke(authentication.getName())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
