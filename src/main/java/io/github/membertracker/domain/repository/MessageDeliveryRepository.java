package io.github.membertracker.domain.repository;

import io.github.membertracker.domain.model.MessageDelivery;

import java.util.List;

public interface MessageDeliveryRepository {
    List<MessageDelivery> findByCommunicationId(Long communicationId);
    
    MessageDelivery save(MessageDelivery delivery);
    
    List<MessageDelivery> saveAll(List<MessageDelivery> deliveries);
}
