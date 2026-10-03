package io.github.membertracker.infrastructure.persistence.repository;

import io.github.membertracker.infrastructure.persistence.entity.MessageDeliveryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface MessageDeliveryJpaRepository extends JpaRepository<MessageDeliveryEntity, Long> {
    List<MessageDeliveryEntity> findByCommunicationId(Long communicationId);

    /** One row per communication and status: {communicationId, status, count}. */
    @Query("select d.communication.id, d.status, count(d) from MessageDeliveryEntity d "
            + "where d.communication.id in :ids group by d.communication.id, d.status")
    List<Object[]> countByCommunicationIdsGroupedByStatus(@Param("ids") Collection<Long> ids);
}
