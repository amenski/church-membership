# Domain Model Strategy: Behavior vs Policy Patterns

## Executive Summary

For the membertracker project, a **hybrid approach** is recommended:
- **Core domain logic** belongs in domain models
- **Cross-cutting business rules** use policy patterns
- **Complex workflows** remain in use cases

## The Problem: Anemic Domain Models

Current state: All domain entities are data containers without behavior:

```java
// Current anemic model
public class Member {
    private int consecutiveMonthsMissed;
    private boolean active;
    
    // Only getters/setters - no behavior
}
```

## Option Analysis

### Option 1: Rich Domain Models (Behavior in Models)

**Pros:**
- Encapsulates business rules with data
- Prevents invalid states
- Follows Object-Oriented Design principles
- Easy to test domain logic in isolation

**Cons:**
- Can become bloated with complex logic
- Harder to test when dependencies are involved
- May violate Single Responsibility Principle

### Option 2: Policy Pattern (External Business Rules)

**Pros:**
- Separates concerns clearly
- Easier to test policies independently
- More flexible for changing business rules
- Better for cross-cutting concerns

**Cons:**
- Can lead to anemic models
- Business rules become disconnected from data
- More complex to understand the system

## Recommended Strategy for MemberTracker

### 1. **Core Domain Logic in Models**
Place intrinsic business rules directly in domain models:

```java
public class Member {
    private int consecutiveMonthsMissed;
    private boolean active;
    
    // Core behavior that only depends on member's own state
    public void recordPayment(Payment payment) {
        this.lastPaymentDate = payment.getPaymentDate();
        if (paymentCoversCurrentPeriod(payment)) {
            this.consecutiveMonthsMissed = 0;
        }
    }
    
    public boolean isPaymentOverdue() {
        return consecutiveMonthsMissed > 0;
    }
    
    private boolean paymentCoversCurrentPeriod(Payment payment) {
        return payment.getPeriod().equals(YearMonth.now());
    }
}
```

### 2. **External Business Rules as Policies**
Create policy classes for rules that involve multiple entities or external factors:

```java
// Policy for membership status
public interface MembershipPolicy {
    boolean shouldDeactivate(Member member, LocalDate currentDate);
    boolean shouldSendReminder(Member member, LocalDate currentDate);
}

// Implementation
public class DefaultMembershipPolicy implements MembershipPolicy {
    private static final int MAX_MISSED_MONTHS = 3;
    private static final int REMINDER_DAYS_BEFORE_DUE = 7;
    
    @Override
    public boolean shouldDeactivate(Member member, LocalDate currentDate) {
        return member.getConsecutiveMonthsMissed() >= MAX_MISSED_MONTHS;
    }
    
    @Override
    public boolean shouldSendReminder(Member member, LocalDate currentDate) {
        YearMonth currentMonth = YearMonth.from(currentDate);
        LocalDate dueDate = currentMonth.atEndOfMonth();
        return currentDate.isAfter(dueDate.minusDays(REMINDER_DAYS_BEFORE_DUE)) &&
               !member.hasPaymentForMonth(currentMonth);
    }
}
```

### 3. **Complex Workflows in Use Cases**
Keep coordination and transaction management in use cases:

```java
public class RecordPaymentUseCase {
    private final PaymentRepository paymentRepository;
    private final MemberRepository memberRepository;
    private final MembershipPolicy membershipPolicy;
    
    @Transactional
    public Payment invoke(Payment payment) {
        Member member = payment.getMember();
        
        // Domain model behavior
        member.recordPayment(payment);
        
        // Policy-based decisions
        if (membershipPolicy.shouldDeactivate(member, LocalDate.now())) {
            member.deactivate();
        }
        
        memberRepository.save(member);
        return paymentRepository.save(payment);
    }
}
```

## Specific Recommendations for Each Entity

### Member Entity
**Behavior in Model:**
- `recordPayment(Payment payment)`
- `markPaymentMissed()`
- `activate()`
- `deactivate()`
- `isActive()`
- `isPaymentOverdue()`

**Policy-Based:**
- Membership status evaluation rules
- Payment reminder timing
- Reactivation criteria

### Payment Entity
**Behavior in Model:**
- `isValidAmount()`
- `isForCurrentPeriod()`
- `calculateLateFee()`

**Policy-Based:**
- Late fee calculation rules
- Payment method validation
- Refund policies

### User Entity
**Behavior in Model:**
- `changePassword(String newPassword)`
- `validatePassword(String password)`
- `enable()`
- `disable()`

**Policy-Based:**
- Password strength requirements
- Account lockout policies
- Role assignment rules

### Communication Entity
**Behavior in Model:**
- `markAsSent()`
- `addRecipient(Member member)`
- `isSent()`

**Policy-Based:**
- Delivery scheduling rules
- Recipient selection criteria
- Retry policies

## Implementation Guidelines

### When to Use Domain Model Behavior:
- Operations that only depend on the entity's own data
- Validation of entity state invariants
- Simple calculations based on entity data
- State transitions that maintain consistency

### When to Use Policy Pattern:
- Rules that involve multiple domain entities
- Business rules that change frequently
- Cross-cutting concerns across the system
- Complex conditional logic
- External dependencies or configuration

### When to Use Use Cases:
- Coordinating multiple domain operations
- Transaction management
- External service calls
- Complex workflows spanning multiple entities

## Testing Strategy

### Domain Model Tests (Unit Tests):
```java
@Test
void recordPayment_resetsMissedCounter_forCurrentPeriod() {
    Member member = new Member("John", "john@test.com", "1234567890");
    member.setConsecutiveMonthsMissed(2);
    
    Payment payment = new Payment(member, YearMonth.now(), BigDecimal.valueOf(50), "CASH");
    member.recordPayment(payment);
    
    assertEquals(0, member.getConsecutiveMonthsMissed());
}
```

### Policy Tests (Unit Tests):
```java
@Test
void shouldDeactivate_returnsTrue_whenThreeMonthsMissed() {
    Member member = new Member("John", "john@test.com", "1234567890");
    member.setConsecutiveMonthsMissed(3);
    
    MembershipPolicy policy = new DefaultMembershipPolicy();
    assertTrue(policy.shouldDeactivate(member, LocalDate.now()));
}
```

### Use Case Tests (Integration Tests):
```java
@Test
void recordPayment_updatesMemberStatus() {
    // Test the complete workflow including policies
}
```

## Migration Strategy

### Phase 1: Identify Core Behaviors
- Analyze current use cases for domain logic
- Extract simple operations into domain models
- Create basic policy interfaces

### Phase 2: Implement Hybrid Approach
- Start with high-value domain behaviors
- Implement critical policies
- Refactor use cases to use new patterns

### Phase 3: Optimize and Refine
- Review and adjust the balance
- Extract common patterns
- Document the strategy

## Benefits of This Approach

1. **Maintainable**: Clear separation of concerns
2. **Testable**: Each component can be tested independently
3. **Flexible**: Policies can be changed without modifying domain models
4. **Scalable**: Complex logic doesn't bloat domain entities
5. **Domain-Focused**: Core business concepts remain clear

This hybrid approach provides the best of both worlds: rich domain models for core business concepts with flexible policies for complex business rules.