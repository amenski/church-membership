package io.github.membertracker.infrastructure.config;

import io.github.membertracker.domain.policy.DefaultMembershipPolicy;
import io.github.membertracker.domain.repository.CommunicationRepository;
import io.github.membertracker.domain.repository.MemberRepository;
import io.github.membertracker.domain.repository.PaymentRepository;
import io.github.membertracker.domain.repository.UserRepository;
import io.github.membertracker.infrastructure.service.EmailService;
import io.github.membertracker.usecase.AuthenticateUserUseCase;
import io.github.membertracker.usecase.ChangePasswordUseCase;
import io.github.membertracker.usecase.CreateCommunicationUseCase;
import io.github.membertracker.usecase.DeleteMemberUseCase;
import io.github.membertracker.usecase.GetActiveMembersUseCase;
import io.github.membertracker.usecase.GetAllCommunicationsUseCase;
import io.github.membertracker.usecase.GetAllMembersUseCase;
import io.github.membertracker.usecase.GetAllPaymentsUseCase;
import io.github.membertracker.usecase.GetCommunicationByIdUseCase;
import io.github.membertracker.usecase.GetCurrentUserUseCase;
import io.github.membertracker.usecase.GetInactiveMembersUseCase;
import io.github.membertracker.usecase.GetMemberByIdUseCase;
import io.github.membertracker.usecase.GetMembersWithMissedPaymentsUseCase;
import io.github.membertracker.usecase.GetMembersWithoutRecentPaymentUseCase;
import io.github.membertracker.usecase.GetPaymentByIdUseCase;
import io.github.membertracker.usecase.GetPaymentsByMemberUseCase;
import io.github.membertracker.usecase.HasPaymentForMonthUseCase;
import io.github.membertracker.usecase.LoadUserByUsernameUseCase;
import io.github.membertracker.usecase.ProcessMemberPaymentUseCase;
import io.github.membertracker.usecase.RecordPaymentUseCase;
import io.github.membertracker.usecase.RegisterUserUseCase;
import io.github.membertracker.usecase.SaveMemberUseCase;
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

    // User-related use cases
    @Bean
    public RegisterUserUseCase registerUserUseCase(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return new RegisterUserUseCase(userRepository, passwordEncoder);
    }

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
    public GetInactiveMembersUseCase getInactiveMembersUseCase(MemberRepository memberRepository) {
        return new GetInactiveMembersUseCase(memberRepository);
    }

    @Bean
    public SaveMemberUseCase saveMemberUseCase(MemberRepository memberRepository) {
        return new SaveMemberUseCase(memberRepository);
    }

    @Bean
    public DeleteMemberUseCase deleteMemberUseCase(MemberRepository memberRepository) {
        return new DeleteMemberUseCase(memberRepository);
    }

    @Bean
    public GetMembersWithMissedPaymentsUseCase getMembersWithMissedPaymentsUseCase(MemberRepository memberRepository) {
        return new GetMembersWithMissedPaymentsUseCase(memberRepository);
    }

    @Bean
    public GetMembersWithoutRecentPaymentUseCase getMembersWithoutRecentPaymentUseCase(MemberRepository memberRepository) {
        return new GetMembersWithoutRecentPaymentUseCase(memberRepository);
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
    public RecordPaymentUseCase recordPaymentUseCase(PaymentRepository paymentRepository, MemberRepository memberRepository) {
        return new RecordPaymentUseCase(paymentRepository, memberRepository);
    }

    @Bean
    public HasPaymentForMonthUseCase hasPaymentForMonthUseCase(PaymentRepository paymentRepository) {
        return new HasPaymentForMonthUseCase(paymentRepository);
    }

    @Bean
    public ProcessMemberPaymentUseCase processMemberPaymentUseCase(MemberRepository memberRepository, PaymentRepository paymentRepository) {
        return new ProcessMemberPaymentUseCase(memberRepository, paymentRepository, new DefaultMembershipPolicy());
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
    public CreateCommunicationUseCase createCommunicationUseCase(CommunicationRepository communicationRepository) {
        return new CreateCommunicationUseCase(communicationRepository);
    }

    @Bean
    public SendCommunicationToAllMembersUseCase sendCommunicationToAllMembersUseCase(
            CommunicationRepository communicationRepository, 
            MemberRepository memberRepository,
            EmailService emailService) {
        return new SendCommunicationToAllMembersUseCase(communicationRepository, memberRepository, emailService);
    }

    @Bean
    public SendCommunicationToMembersUseCase sendCommunicationToMembersUseCase(
            CommunicationRepository communicationRepository,
            EmailService emailService) {
        return new SendCommunicationToMembersUseCase(communicationRepository, emailService);
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