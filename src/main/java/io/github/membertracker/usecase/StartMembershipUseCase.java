package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.ActivityType;
import io.github.membertracker.domain.enumeration.MemberStatus;
import io.github.membertracker.domain.exception.PersonDomainException;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.Person;
import io.github.membertracker.domain.repository.MemberRepository;
import io.github.membertracker.domain.repository.PersonRepository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Turns a person into a member: the new membership follows the rules of {@link SaveMemberUseCase} (MEMBER or INACTIVE,
 * counters zero, join date today unless given) and is linked to the existing person, so dues, reminders and messages
 * start for them. The activity entry carries the name only.
 */
public class StartMembershipUseCase {

    private final PersonRepository personRepository;
    private final MemberRepository memberRepository;
    private final RecordActivityUseCase recordActivity;

    public StartMembershipUseCase(PersonRepository personRepository, MemberRepository memberRepository,
                                  RecordActivityUseCase recordActivity) {
        this.personRepository = personRepository;
        this.memberRepository = memberRepository;
        this.recordActivity = recordActivity;
    }

    /**
     * @param joinDate null means today
     * @param status   null means MEMBER; only MEMBER and INACTIVE are allowed (MemberDomainException, 400)
     * @return the person with their new membership, or empty when there is no person with that id
     * @throws PersonDomainException (409) when the person already has a membership
     */
    public Optional<Person> invoke(Long personId, LocalDate joinDate, MemberStatus status) {
        return personRepository.findById(personId).map(person -> {
            if (person.hasMembership()) {
                throw PersonDomainException.alreadyMember();
            }
            Member member = SaveMemberUseCase.newMember(person.name(), person.email(), person.phone(), joinDate, status);
            memberRepository.saveForPerson(personId, member);
            recordActivity.record(ActivityType.MEMBERSHIP_STARTED, "Person " + person.name() + " became a member",
                    "PERSON", personId);
            return personRepository.findById(personId).orElseThrow();
        });
    }
}
