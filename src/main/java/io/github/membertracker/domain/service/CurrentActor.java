package io.github.membertracker.domain.service;

/** Who is acting right now, so the use cases can record it without knowing about Spring Security. */
public interface CurrentActor {

    String SYSTEM = "system";

    /** The signed-in user's email, or {@link #SYSTEM} when nobody is signed in (the scheduler, a sign-in in progress). */
    String getEmail();
}
