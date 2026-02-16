package io.github.membertracker.infrastructure.persistence.repository;

import io.github.membertracker.infrastructure.persistence.entity.MessageDeliveryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageDeliveryJpaRepository extends JpaRepository<MessageDeliveryEntity, Long> {
    List<MessageDeliveryEntity> findByCommunicationId(Long communicationId);
}
