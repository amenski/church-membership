package io.github.membertracker.infrastructure.persistence.repository;

import io.github.membertracker.domain.enumeration.CommunicationType;
import io.github.membertracker.domain.model.Communication;
import io.github.membertracker.domain.model.MessageDelivery;
import io.github.membertracker.domain.repository.CommunicationRepository;
import io.github.membertracker.infrastructure.persistence.entity.CommunicationEntity;
import io.github.membertracker.infrastructure.persistence.entity.MessageDeliveryEntity;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class CommunicationDbRepository implements CommunicationRepository {

    private final CommunicationJpaRepository communicationJpaRepository;
    private final MemberDbRepository memberDbRepository;
    private final MemberJpaRepository memberJpaRepository;

    public CommunicationDbRepository(CommunicationJpaRepository communicationJpaRepository,
                                     MemberDbRepository memberDbRepository,
                                     MemberJpaRepository memberJpaRepository) {
        this.communicationJpaRepository = communicationJpaRepository;
        this.memberDbRepository = memberDbRepository;
        this.memberJpaRepository = memberJpaRepository;
    }

    @Override
    public List<Communication> findAll() {
        return communicationJpaRepository.findAll().stream()
                .map(this::mapToCommunication)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Communication> findById(Long id) {
        return communicationJpaRepository.findById(id)
                .map(this::mapToCommunication);
    }

    @Override
    public List<Communication> findByType(CommunicationType type) {
        CommunicationEntity.CommunicationType entityType = mapToEntityType(type);
        return communicationJpaRepository.findByType(entityType).stream()
                .map(this::mapToCommunication)
                .collect(Collectors.toList());
    }

    @Override
    public List<Communication> findBySentDateBetween(LocalDateTime start, LocalDateTime end) {
        return communicationJpaRepository.findBySentDateBetween(start, end).stream()
                .map(this::mapToCommunication)
                .collect(Collectors.toList());
    }

    @Override
    public Communication save(Communication communication) {
        CommunicationEntity entity = mapToEntity(communication);
        CommunicationEntity saved = communicationJpaRepository.save(entity);

        Communication result = mapToCommunication(saved);
        attachDeliveries(result, communication.getDeliveries(), saved.getDeliveries());
        return result;
    }

    /**
     * The returned communication carries the caller's own deliveries (their recipients are already full
     * members, so nothing is loaded lazily), with the ids the database generated and a pointer back to it.
     */
    private void attachDeliveries(Communication result, List<MessageDelivery> deliveries,
                                  List<MessageDeliveryEntity> savedDeliveries) {
        if (deliveries == null) {
            return;
        }
        for (int i = 0; i < deliveries.size() && i < savedDeliveries.size(); i++) {
            deliveries.get(i).setId(savedDeliveries.get(i).getId());
        }
        for (MessageDelivery delivery : deliveries) {
            delivery.setCommunication(result);
        }
        result.setDeliveries(deliveries);
    }

    private Communication mapToCommunication(CommunicationEntity entity) {
        Communication communication = new Communication();
        communication.setId(entity.getId());
        communication.setTitle(entity.getTitle());
        communication.setMessageContent(entity.getMessageContent());
        communication.setCreatedDate(entity.getCreatedDate());
        communication.setSentDate(entity.getSentDate());
        communication.setType(mapToDomainType(entity.getType()));
        communication.setSentToAllMembers(entity.isSentToAllMembers());

        // Deliveries are not loaded here: they are served by MessageDeliveryRepository (GET /{id}/deliveries)

        return communication;
    }

    private CommunicationEntity mapToEntity(Communication communication) {
        CommunicationEntity entity = new CommunicationEntity();
        entity.setId(communication.getId());
        entity.setTitle(communication.getTitle());
        entity.setMessageContent(communication.getMessageContent());
        entity.setCreatedDate(communication.getCreatedDate());
        entity.setSentDate(communication.getSentDate());
        entity.setType(mapToEntityType(communication.getType()));
        entity.setSentToAllMembers(communication.isSentToAllMembers());

        // Deliveries ride on the cascade. findAll/findById never load them (see GET /{id}/deliveries), so a
        // communication saved without deliveries leaves the stored delivery rows alone: there is no orphanRemoval.
        if (communication.getDeliveries() != null) {
            List<MessageDeliveryEntity> deliveries = new ArrayList<>();
            for (MessageDelivery delivery : communication.getDeliveries()) {
                deliveries.add(mapToDeliveryEntity(delivery, entity));
            }
            entity.setDeliveries(deliveries);
        }

        return entity;
    }

    private MessageDeliveryEntity mapToDeliveryEntity(MessageDelivery delivery, CommunicationEntity communication) {
        MessageDeliveryEntity entity = new MessageDeliveryEntity();
        entity.setId(delivery.getId());
        entity.setCommunication(communication);
        if (delivery.getRecipient() != null && delivery.getRecipient().getId() != null) {
            entity.setRecipient(memberJpaRepository.getReferenceById(delivery.getRecipient().getId()));
        }
        entity.setStatus(delivery.getStatus() == null ? null
                : MessageDeliveryEntity.DeliveryStatus.valueOf(delivery.getStatus().name()));
        entity.setChannel(delivery.getChannel() == null ? null
                : MessageDeliveryEntity.DeliveryChannel.valueOf(delivery.getChannel().name()));
        entity.setDeliveryTime(delivery.getDeliveryTime());
        entity.setResponseNotes(delivery.getResponseNotes());
        return entity;
    }

    private CommunicationType mapToDomainType(CommunicationEntity.CommunicationType entityType) {
        if (entityType == null) return null;
        switch (entityType) {
            case ANNOUNCEMENT: return CommunicationType.ANNOUNCEMENT;
            case REMINDER: return CommunicationType.REMINDER;
            case PERSONAL: return CommunicationType.PERSONAL;
            default: throw new IllegalArgumentException("Unknown communication type: " + entityType);
        }
    }

    private CommunicationEntity.CommunicationType mapToEntityType(CommunicationType domainType) {
        if (domainType == null) return null;
        switch (domainType) {
            case ANNOUNCEMENT: return CommunicationEntity.CommunicationType.ANNOUNCEMENT;
            case REMINDER: return CommunicationEntity.CommunicationType.REMINDER;
            case PERSONAL: return CommunicationEntity.CommunicationType.PERSONAL;
            default: throw new IllegalArgumentException("Unknown communication type: " + domainType);
        }
    }
}
