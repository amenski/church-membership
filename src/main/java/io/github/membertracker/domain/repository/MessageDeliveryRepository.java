package io.github.membertracker.domain.repository;

import io.github.membertracker.domain.model.MessageDelivery;

import java.util.List;
import java.util.Optional;

public interface MessageDeliveryRepository {
    Optional<MessageDelivery> findById(Long id);

    List<MessageDelivery> findByCommunicationId(Long communicationId);
    
    MessageDelivery save(MessageDelivery delivery);
    
    List<MessageDelivery> saveAll(List<MessageDelivery> deliveries);
}
