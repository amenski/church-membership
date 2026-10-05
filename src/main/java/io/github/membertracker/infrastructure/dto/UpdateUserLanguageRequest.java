package io.github.membertracker.infrastructure.dto;

import jakarta.validation.constraints.Pattern;

/** The UI language to save on the account. A null passes, so a client that omits it changes nothing. */
public class UpdateUserLanguageRequest {

    @Pattern(regexp = "^(am|en)$", message = "Language must be am or en")
    private String language;

    public UpdateUserLanguageRequest() {}

    public UpdateUserLanguageRequest(String language) {
        this.language = language;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }
}
