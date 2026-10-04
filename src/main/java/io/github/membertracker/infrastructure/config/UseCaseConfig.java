package io.github.membertracker.infrastructure.config;

import io.github.membertracker.domain.repository.ActivityLogRepository;
import io.github.membertracker.domain.repository.CommunicationRepository;
import io.github.membertracker.domain.repository.HouseholdRepository;
import io.github.membertracker.domain.repository.MessageDeliveryRepository;
import io.github.membertracker.domain.repository.MemberRepository;
import io.github.membertracker.domain.repository.PaymentRepository;
import io.github.membertracker.domain.repository.PersonRepository;
import io.github.membertracker.domain.repository.UserRepository;
import io.github.membertracker.domain.service.CurrentActor;
import io.github.membertracker.infrastructure.service.EmailService;
import io.github.membertracker.usecase.AuthenticateUserUseCase;
import io.github.membertracker.usecase.GetActivityLogUseCase;
import io.github.membertracker.usecase.RecordActivityUseCase;
import io.github.membertracker.usecase.ChangePasswordUseCase;
import io.github.membertracker.usecase.ArchiveMemberUseCase;
import io.github.membertracker.usecase.CreateHouseholdUseCase;
import io.github.membertracker.usecase.CreatePersonUseCase;
import io.github.membertracker.usecase.DeleteHouseholdUseCase;
import io.github.membertracker.usecase.DeletePersonUseCase;
import io.github.membertracker.usecase.GetPeopleUseCase;
import io.github.membertracker.usecase.GetPersonByIdUseCase;
import io.github.membertracker.usecase.StartMembershipUseCase;
import io.github.membertracker.usecase.UpdatePersonUseCase;
import io.github.membertracker.usecase.GetAllHouseholdsUseCase;
import io.github.membertracker.usecase.GetHouseholdByIdUseCase;
import io.github.membertracker.usecase.UpdateHouseholdUseCase;
import io.github.membertracker.usecase.DeleteMemberPermanentlyUseCase;
import io.github.membertracker.usecase.GetActiveMembersUseCase;
import io.github.membertracker.usecase.GetArchivedMembersUseCase;import io.github.membertracker.usecase.GetAllCommunicationsUseCase;
import io.github.membertracker.usecase.GetAllMembersUseCase;
import io.github.membertracker.usecase.GetAllPaymentsUseCase;
import io.github.membertracker.usecase.GetCommunicationByIdUseCase;
import io.github.membertracker.usecase.GetCurrentUserUseCase;
import io.github.membertracker.usecase.GetDeliveriesByCommunicationUseCase;
import io.github.membertracker.usecase.RetryDeliveryUseCase;
import io.github.membertracker.usecase.GetInactiveMembersUseCase;
import io.github.membertracker.usecase.GetMemberByIdUseCase;
import io.github.membertracker.usecase.GetMembersWithMissedPaymentsUseCase;
import io.github.membertracker.usecase.GetPaymentByIdUseCase;
import io.github.membertracker.usecase.GetCollectedByMonthUseCase;
import io.github.membertracker.usecase.GetDashboardStatsUseCase;
import io.github.membertracker.usecase.GetRecentCommunicationsUseCase;
import io.github.membertracker.usecase.GetRecentPaymentsUseCase;
import io.github.membertracker.usecase.GetPaymentsByMemberUseCase;
import io.github.membertracker.usecase.HasPaymentForMonthUseCase;
import io.github.membertracker.usecase.LoadUserByUsernameUseCase;
import io.github.membertracker.usecase.RecordPaymentUseCase;
import io.github.membertracker.usecase.SaveMemberUseCase;
import io.github.membertracker.usecase.UpdateMemberUseCase;
import io.github.membertracker.usecase.SendCommunicationToAllMembersUseCase;
import io.github.membertracker.usecase.SendCommunicationToMembersUseCase;
import io.github.membertracker.usecase.SendPaymentRemindersUseCase;
import io.github.membertracker.usecase.UpdateMissingPaymentCountersUseCase;
import io.github.membertracker.usecase.UpdateUserProfileUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class UseCaseConfig {

    // Audit trail
    @Bean
    public RecordActivityUseCase recordActivityUseCase(ActivityLogRepository activityLogRepository, CurrentActor currentActor) {
        return new RecordActivityUseCase(activityLogRepository, currentActor);
    }

    @Bean
    public GetActivityLogUseCase getActivityLogUseCase(ActivityLogRepository activityLogRepository) {
        return new GetActivityLogUseCase(activityLogRepository);
    }

    // User-related use cases
    @Bean
    public AuthenticateUserUseCase authenticateUserUseCase(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return new AuthenticateUserUseCase(userRepository, passwordEncoder);
    }

    @Bean
    public LoadUserByUsernameUseCase loadUserByUsernameUseCase(UserRepository userRepository) {
        return new LoadUserByUsernameUseCase(userRepository);
    }

    @Bean
    public GetCurrentUserUseCase getCurrentUserUseCase(UserRepository userRepository) {
        return new GetCurrentUserUseCase(userRepository);
    }

    @Bean
    public UpdateUserProfileUseCase updateUserProfileUseCase(UserRepository userRepository) {
        return new UpdateUserProfileUseCase(userRepository);
    }

    @Bean
    public ChangePasswordUseCase changePasswordUseCase(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return new ChangePasswordUseCase(userRepository, passwordEncoder);
    }

    // Member-related use cases
    @Bean
    public GetAllMembersUseCase getAllMembersUseCase(MemberRepository memberRepository) {
        return new GetAllMembersUseCase(memberRepository);
    }

    @Bean
    public GetMemberByIdUseCase getMemberByIdUseCase(MemberRepository memberRepository) {
        return new GetMemberByIdUseCase(memberRepository);
    }

    @Bean
    public GetActiveMembersUseCase getActiveMembersUseCase(MemberRepository memberRepository) {
        return new GetActiveMembersUseCase(memberRepository);
    }

    @Bean
    public GetArchivedMembersUseCase getArchivedMembersUseCase(MemberRepository memberRepository) {
        return new GetArchivedMembersUseCase(memberRepository);
    }

    @Bean
    public GetInactiveMembersUseCase getInactiveMembersUseCase(MemberRepository memberRepository) {
        return new GetInactiveMembersUseCase(memberRepository);
    }

    @Bean
    public SaveMemberUseCase saveMemberUseCase(MemberRepository memberRepository, RecordActivityUseCase recordActivityUseCase) {
        return new SaveMemberUseCase(memberRepository, recordActivityUseCase);
    }

    @Bean
    public UpdateMemberUseCase updateMemberUseCase(MemberRepository memberRepository, RecordActivityUseCase recordActivityUseCase) {
        return new UpdateMemberUseCase(memberRepository, recordActivityUseCase);
    }

    @Bean
    public ArchiveMemberUseCase archiveMemberUseCase(MemberRepository memberRepository, RecordActivityUseCase recordActivityUseCase) {
        return new ArchiveMemberUseCase(memberRepository, recordActivityUseCase);
    }

    @Bean
    public DeleteMemberPermanentlyUseCase deleteMemberPermanentlyUseCase(MemberRepository memberRepository,
                                                                         PaymentRepository paymentRepository,
                                                                         MessageDeliveryRepository messageDeliveryRepository,
                                                                         RecordActivityUseCase recordActivityUseCase) {
        return new DeleteMemberPermanentlyUseCase(memberRepository, paymentRepository, messageDeliveryRepository,
                recordActivityUseCase);
    }

    @Bean
    public GetAllHouseholdsUseCase getAllHouseholdsUseCase(HouseholdRepository householdRepository) {
        return new GetAllHouseholdsUseCase(householdRepository);
    }

    @Bean
    public GetHouseholdByIdUseCase getHouseholdByIdUseCase(HouseholdRepository householdRepository) {
        return new GetHouseholdByIdUseCase(householdRepository);
    }

    @Bean
    public CreateHouseholdUseCase createHouseholdUseCase(HouseholdRepository householdRepository,
                                                         RecordActivityUseCase recordActivityUseCase) {
        return new CreateHouseholdUseCase(householdRepository, recordActivityUseCase);
    }

    @Bean
    public UpdateHouseholdUseCase updateHouseholdUseCase(HouseholdRepository householdRepository,
                                                         RecordActivityUseCase recordActivityUseCase) {
        return new UpdateHouseholdUseCase(householdRepository, recordActivityUseCase);
    }

    @Bean
    public DeleteHouseholdUseCase deleteHouseholdUseCase(HouseholdRepository householdRepository,
                                                         RecordActivityUseCase recordActivityUseCase) {
        return new DeleteHouseholdUseCase(householdRepository, recordActivityUseCase);
    }

    // People (members and dependents without a membership)
    @Bean
    public GetPeopleUseCase getPeopleUseCase(PersonRepository personRepository) {
        return new GetPeopleUseCase(personRepository);
    }

    @Bean
    public GetPersonByIdUseCase getPersonByIdUseCase(PersonRepository personRepository) {
        return new GetPersonByIdUseCase(personRepository);
    }

    @Bean
    public CreatePersonUseCase createPersonUseCase(PersonRepository personRepository,
                                                   RecordActivityUseCase recordActivityUseCase) {
        return new CreatePersonUseCase(personRepository, recordActivityUseCase);
    }

    @Bean
    public UpdatePersonUseCase updatePersonUseCase(PersonRepository personRepository,
                                                   RecordActivityUseCase recordActivityUseCase) {
        return new UpdatePersonUseCase(personRepository, recordActivityUseCase);
    }

    @Bean
    public DeletePersonUseCase deletePersonUseCase(PersonRepository personRepository,
                                                   RecordActivityUseCase recordActivityUseCase) {
        return new DeletePersonUseCase(personRepository, recordActivityUseCase);
    }

    @Bean
    public StartMembershipUseCase startMembershipUseCase(PersonRepository personRepository,
                                                         MemberRepository memberRepository,
                                                         RecordActivityUseCase recordActivityUseCase) {
        return new StartMembershipUseCase(personRepository, memberRepository, recordActivityUseCase);
    }

    @Bean
    public GetMembersWithMissedPaymentsUseCase getMembersWithMissedPaymentsUseCase(MemberRepository memberRepository) {
        return new GetMembersWithMissedPaymentsUseCase(memberRepository);
    }

    // Payment-related use cases
    @Bean
    public GetAllPaymentsUseCase getAllPaymentsUseCase(PaymentRepository paymentRepository) {
        return new GetAllPaymentsUseCase(paymentRepository);
    }

    @Bean
    public GetPaymentByIdUseCase getPaymentByIdUseCase(PaymentRepository paymentRepository) {
        return new GetPaymentByIdUseCase(paymentRepository);
    }

    @Bean
    public GetPaymentsByMemberUseCase getPaymentsByMemberUseCase(PaymentRepository paymentRepository) {
        return new GetPaymentsByMemberUseCase(paymentRepository);
    }

    @Bean
    public RecordPaymentUseCase recordPaymentUseCase(PaymentRepository paymentRepository, MemberRepository memberRepository,
                                                   RecordActivityUseCase recordActivityUseCase) {
        return new RecordPaymentUseCase(paymentRepository, memberRepository, recordActivityUseCase);
    }

    @Bean
    public HasPaymentForMonthUseCase hasPaymentForMonthUseCase(PaymentRepository paymentRepository) {
        return new HasPaymentForMonthUseCase(paymentRepository);
    }

    // Communication-related use cases
    @Bean
    public GetAllCommunicationsUseCase getAllCommunicationsUseCase(CommunicationRepository communicationRepository) {
        return new GetAllCommunicationsUseCase(communicationRepository);
    }

    @Bean
    public GetCommunicationByIdUseCase getCommunicationByIdUseCase(CommunicationRepository communicationRepository) {
        return new GetCommunicationByIdUseCase(communicationRepository);
    }

    @Bean
    public GetDeliveriesByCommunicationUseCase getDeliveriesByCommunicationUseCase(
            CommunicationRepository communicationRepository,
            MessageDeliveryRepository messageDeliveryRepository) {
        return new GetDeliveriesByCommunicationUseCase(communicationRepository, messageDeliveryRepository);
    }

    @Bean
    public RetryDeliveryUseCase retryDeliveryUseCase(
            CommunicationRepository communicationRepository,
            MessageDeliveryRepository messageDeliveryRepository,
            EmailService emailService) {
        return new RetryDeliveryUseCase(communicationRepository, messageDeliveryRepository, emailService);
    }

    @Bean
    public SendCommunicationToAllMembersUseCase sendCommunicationToAllMembersUseCase(
            CommunicationRepository communicationRepository, 
            MemberRepository memberRepository,
            MessageDeliveryRepository messageDeliveryRepository,
            EmailService emailService,
            RecordActivityUseCase recordActivityUseCase) {
        return new SendCommunicationToAllMembersUseCase(communicationRepository, memberRepository,
                messageDeliveryRepository, emailService, recordActivityUseCase);
    }

    @Bean
    public SendCommunicationToMembersUseCase sendCommunicationToMembersUseCase(
            CommunicationRepository communicationRepository,
            MessageDeliveryRepository messageDeliveryRepository,
            EmailService emailService,
            RecordActivityUseCase recordActivityUseCase) {
        return new SendCommunicationToMembersUseCase(communicationRepository, messageDeliveryRepository, emailService,
                recordActivityUseCase);
    }

    // Dashboard use cases
    @Bean
    public GetDashboardStatsUseCase getDashboardStatsUseCase(MemberRepository memberRepository, PaymentRepository paymentRepository) {
        return new GetDashboardStatsUseCase(memberRepository, paymentRepository);
    }

    @Bean
    public GetCollectedByMonthUseCase getCollectedByMonthUseCase(PaymentRepository paymentRepository) {
        return new GetCollectedByMonthUseCase(paymentRepository);
    }

    @Bean
    public GetRecentPaymentsUseCase getRecentPaymentsUseCase(PaymentRepository paymentRepository) {
        return new GetRecentPaymentsUseCase(paymentRepository);
    }

    @Bean
    public GetRecentCommunicationsUseCase getRecentCommunicationsUseCase(CommunicationRepository communicationRepository) {
        return new GetRecentCommunicationsUseCase(communicationRepository);
    }

    // Scheduler-related use cases
    @Bean
    public UpdateMissingPaymentCountersUseCase updateMissingPaymentCountersUseCase(MemberRepository memberRepository, HasPaymentForMonthUseCase hasPaymentForMonthUseCase) {
        return new UpdateMissingPaymentCountersUseCase(memberRepository,  hasPaymentForMonthUseCase);
    }

    @Bean
    public SendPaymentRemindersUseCase sendPaymentRemindersUseCase(MemberRepository memberRepository, SendCommunicationToMembersUseCase  sendCommunicationToMembersUseCase) {
        return new SendPaymentRemindersUseCase(memberRepository, sendCommunicationToMembersUseCase);
    }
}