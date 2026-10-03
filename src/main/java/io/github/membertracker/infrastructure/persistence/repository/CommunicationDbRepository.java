package io.github.membertracker.infrastructure.persistence.repository;

import io.github.membertracker.domain.enumeration.CommunicationType;
import io.github.membertracker.domain.model.Communication;
import io.github.membertracker.domain.model.DeliverySummary;
import io.github.membertracker.domain.model.MessageDelivery;
import io.github.membertracker.domain.repository.CommunicationRepository;
import io.github.membertracker.infrastructure.persistence.entity.CommunicationEntity;
import io.github.membertracker.infrastructure.persistence.entity.MessageDeliveryEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class CommunicationDbRepository implements CommunicationRepository {

    private final CommunicationJpaRepository communicationJpaRepository;
    private final MemberDbRepository memberDbRepository;
    private final MemberJpaRepository memberJpaRepository;
    private final MessageDeliveryJpaRepository messageDeliveryJpaRepository;

    public CommunicationDbRepository(CommunicationJpaRepository communicationJpaRepository,
                                     MemberDbRepository memberDbRepository,
                                     MemberJpaRepository memberJpaRepository,
                                     MessageDeliveryJpaRepository messageDeliveryJpaRepository) {
        this.communicationJpaRepository = communicationJpaRepository;
        this.memberDbRepository = memberDbRepository;
        this.memberJpaRepository = memberJpaRepository;
        this.messageDeliveryJpaRepository = messageDeliveryJpaRepository;
    }

    @Override
    public List<Communication> findAll() {
        List<Communication> communications = communicationJpaRepository.findAll().stream()
                .map(this::mapToCommunication)
                .collect(Collectors.toList());
        fillDeliveryCounts(communications);
        return communications;
    }

    @Override
    public List<Communication> findRecent(int limit) {
        Sort newestFirst = Sort.by(Sort.Order.desc("createdDate"), Sort.Order.desc("id"));
        return communicationJpaRepository.findAll(PageRequest.of(0, limit, newestFirst)).stream()
                .map(this::mapToCommunication)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Communication> findById(Long id) {
        Optional<Communication> found = communicationJpaRepository.findById(id)
                .map(this::mapToCommunication);
        found.ifPresent(c -> fillDeliveryCounts(List.of(c)));
        return found;
    }

    /** One grouped query for all the given communications: recipient count and deliveries per status. */
    private void fillDeliveryCounts(List<Communication> communications) {
        List<Long> ids = communications.stream().map(Communication::getId).toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, int[]> perCommunication = new HashMap<>();
        for (Object[] row : messageDeliveryJpaRepository.countByCommunicationIdsGroupedByStatus(ids)) {
            int[] counts = perCommunication.computeIfAbsent((Long) row[0], k -> new int[4]);
            counts[statusIndex((MessageDeliveryEntity.DeliveryStatus) row[1])] += ((Number) row[2]).intValue();
        }
        for (Communication communication : communications) {
            int[] counts = perCommunication.getOrDefault(communication.getId(), new int[4]);
            applyCounts(communication, counts);
        }
    }

    private static int statusIndex(MessageDeliveryEntity.DeliveryStatus status) {
        if (status == null) {
            return 2; // a delivery without a status has not been sent yet
        }
        return switch (status) {
            case SENT -> 0;
            case FAILED -> 1;
            case PENDING -> 2;
            case DELIVERED -> 3;
        };
    }

    private static void applyCounts(Communication communication, int[] counts) {
        communication.setDeliverySummary(new DeliverySummary(counts[0], counts[1], counts[2], counts[3]));
        communication.setRecipientCount(counts[0] + counts[1] + counts[2] + counts[3]);
    }

    @Override
    public Communication save(Communication communication) {
        CommunicationEntity entity = mapToEntity(communication);
        CommunicationEntity saved = communicationJpaRepository.save(entity);

        Communication result = mapToCommunication(saved);
        attachDeliveries(result, communication.getDeliveries(), saved.getDeliveries());
        if (communication.getDeliveries() != null && !communication.getDeliveries().isEmpty()) {
            int[] counts = new int[4];
            for (MessageDelivery delivery : communication.getDeliveries()) {
                counts[statusIndex(delivery.getStatus() == null ? null
                        : MessageDeliveryEntity.DeliveryStatus.valueOf(delivery.getStatus().name()))]++;
            }
            applyCounts(result, counts);
        } else {
            // saved without deliveries: the stored rows (if any) are untouched, so ask the database
            fillDeliveryCounts(List.of(result));
        }
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
