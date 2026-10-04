package io.github.membertracker.domain.repository;

import io.github.membertracker.domain.model.MessageDelivery;

import java.util.List;
import java.util.Optional;

public interface MessageDeliveryRepository {
    Optional<MessageDelivery> findById(Long id);

    List<MessageDelivery> findByCommunicationId(Long communicationId);
    
    /** How many deliveries (any status) were addressed to this member. */
    long countByRecipientId(Long memberId);

    MessageDelivery save(MessageDelivery delivery);
    
    List<MessageDelivery> saveAll(List<MessageDelivery> deliveries);
}
