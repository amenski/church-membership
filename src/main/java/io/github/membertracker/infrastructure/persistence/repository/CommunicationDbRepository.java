package io.github.membertracker.infrastructure.persistence.repository;

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

    public CommunicationDbRepository(CommunicationJpaRepository communicationJpaRepository, MemberDbRepository memberDbRepository) {
        this.communicationJpaRepository = communicationJpaRepository;
        this.memberDbRepository = memberDbRepository;
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
    public List<Communication> findByType(Communication.CommunicationType type) {
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
        return mapToCommunication(communicationJpaRepository.save(entity));
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

        // Map deliveries if needed
        // This is a simplified version, in a real application you would need to map the deliveries as well

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

        // Map deliveries if needed
        // This is a simplified version, in a real application you would need to map the deliveries as well

        return entity;
    }

    private Communication.CommunicationType mapToDomainType(CommunicationEntity.CommunicationType entityType) {
        if (entityType == null) return null;
        switch (entityType) {
            case ANNOUNCEMENT: return Communication.CommunicationType.ANNOUNCEMENT;
            case REMINDER: return Communication.CommunicationType.REMINDER;
            case PERSONAL: return Communication.CommunicationType.PERSONAL;
            default: throw new IllegalArgumentException("Unknown communication type: " + entityType);
        }
    }

    private CommunicationEntity.CommunicationType mapToEntityType(Communication.CommunicationType domainType) {
        if (domainType == null) return null;
        switch (domainType) {
            case ANNOUNCEMENT: return CommunicationEntity.CommunicationType.ANNOUNCEMENT;
            case REMINDER: return CommunicationEntity.CommunicationType.REMINDER;
            case PERSONAL: return CommunicationEntity.CommunicationType.PERSONAL;
            default: throw new IllegalArgumentException("Unknown communication type: " + domainType);
        }
    }
}
