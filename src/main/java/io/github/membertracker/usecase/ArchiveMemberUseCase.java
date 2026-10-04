package io.github.membertracker.usecase;

import io.github.membertracker.domain.enumeration.ActivityType;
import io.github.membertracker.domain.enumeration.MemberStatus;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.MemberRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * "Delete" in the screens: the member becomes ARCHIVED and disappears from the lists, but the row, the payments and
 * the message history stay. Nothing is ever deleted here.
 */
public class ArchiveMemberUseCase {

    private final MemberRepository memberRepository;
    private final RecordActivityUseCase recordActivity;
    private final Clock clock;

    public ArchiveMemberUseCase(MemberRepository memberRepository, RecordActivityUseCase recordActivity) {
        this(memberRepository, recordActivity, Clock.systemDefaultZone());
    }

    public ArchiveMemberUseCase(MemberRepository memberRepository, RecordActivityUseCase recordActivity, Clock clock) {
        this.memberRepository = memberRepository;
        this.recordActivity = recordActivity;
        this.clock = clock;
    }

    /**
     * @return the archived member, or empty when there is no member with that id; archiving an archived member
     *         changes nothing and records nothing
     */
    public Optional<Member> invoke(Long id) {
        return memberRepository.findById(id).map(member -> {
            if (member.getStatus() == MemberStatus.ARCHIVED) {
                return member;
            }
            member.archive(LocalDateTime.now(clock));
            Member saved = memberRepository.save(member);
            recordActivity.record(ActivityType.MEMBER_ARCHIVED, "Member " + saved.getName() + " was archived",
                    "MEMBER", saved.getId());
            return saved;
        });
    }
}
