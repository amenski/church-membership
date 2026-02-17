package io.github.membertracker.infrastructure.persistence.repository;

import io.github.membertracker.domain.model.Communication;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.model.MessageDelivery;
import io.github.membertracker.domain.repository.MessageDeliveryRepository;
import io.github.membertracker.infrastructure.persistence.entity.CommunicationEntity;
import io.github.membertracker.infrastructure.persistence.entity.MemberEntity;
import io.github.membertracker.infrastructure.persistence.entity.MessageDeliveryEntity;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class MessageDeliveryDbRepository implements MessageDeliveryRepository {

    private final MessageDeliveryJpaRepository messageDeliveryJpaRepository;
    private final MemberJpaRepository memberJpaRepository;
    private final CommunicationJpaRepository communicationJpaRepository;

    public MessageDeliveryDbRepository(MessageDeliveryJpaRepository messageDeliveryJpaRepository,
                                       MemberJpaRepository memberJpaRepository,
                                       CommunicationJpaRepository communicationJpaRepository) {
        this.messageDeliveryJpaRepository = messageDeliveryJpaRepository;
        this.memberJpaRepository = memberJpaRepository;
        this.communicationJpaRepository = communicationJpaRepository;
    }

    @Override
    public List<MessageDelivery> findByCommunicationId(Long communicationId) {
        return messageDeliveryJpaRepository.findByCommunicationId(communicationId).stream()
                .map(this::mapToDomain)
                .collect(Collectors.toList());
    }

    @Override
    public MessageDelivery save(MessageDelivery delivery) {
        MessageDeliveryEntity entity = mapToEntity(delivery);
        return mapToDomain(messageDeliveryJpaRepository.save(entity));
    }

    @Override
    public List<MessageDelivery> saveAll(List<MessageDelivery> deliveries) {
        List<MessageDeliveryEntity> entities = deliveries.stream()
                .map(this::mapToEntity)
                .collect(Collectors.toList());
        return messageDeliveryJpaRepository.saveAll(entities).stream()
                .map(this::mapToDomain)
                .collect(Collectors.toList());
    }

    private MessageDelivery mapToDomain(MessageDeliveryEntity entity) {
        MessageDelivery delivery = new MessageDelivery();
        delivery.setId(entity.getId());
        delivery.setStatus(mapToDomainStatus(entity.getStatus()));
        delivery.setChannel(mapToDomainChannel(entity.getChannel()));
        delivery.setDeliveryTime(entity.getDeliveryTime());
        delivery.setResponseNotes(entity.getResponseNotes());

        // Map recipient
        if (entity.getRecipient() != null) {
            Member member = new Member();
            member.setId(entity.getRecipient().getId());
            member.setName(entity.getRecipient().getName());
            member.setEmail(entity.getRecipient().getEmail());
            member.setPhone(entity.getRecipient().getPhone());
            member.setActive(entity.getRecipient().isActive());
            delivery.setRecipient(member);
        }

        // Map communication (minimal info)
        if (entity.getCommunication() != null) {
            Communication comm = new Communication();
            comm.setId(entity.getCommunication().getId());
            delivery.setCommunication(comm);
        }

        return delivery;
    }

    private MessageDeliveryEntity mapToEntity(MessageDelivery delivery) {
        MessageDeliveryEntity entity = new MessageDeliveryEntity();
        if (delivery.getId() != null) {
            entity.setId(delivery.getId());
        }
        entity.setStatus(mapToEntityStatus(delivery.getStatus()));
        entity.setChannel(mapToEntityChannel(delivery.getChannel()));
        entity.setDeliveryTime(delivery.getDeliveryTime());
        entity.setResponseNotes(delivery.getResponseNotes());

        // Map recipient
        if (delivery.getRecipient() != null && delivery.getRecipient().getId() != null) {
            memberJpaRepository.findById(delivery.getRecipient().getId())
                    .ifPresent(entity::setRecipient);
        }

        // Map communication
        if (delivery.getCommunication() != null && delivery.getCommunication().getId() != null) {
            communicationJpaRepository.findById(delivery.getCommunication().getId())
                    .ifPresent(entity::setCommunication);
        }

        return entity;
    }

    private MessageDelivery.DeliveryStatus mapToDomainStatus(MessageDeliveryEntity.DeliveryStatus entityStatus) {
        if (entityStatus == null) return null;
        return MessageDelivery.DeliveryStatus.valueOf(entityStatus.name());
    }

    private MessageDeliveryEntity.DeliveryStatus mapToEntityStatus(MessageDelivery.DeliveryStatus domainStatus) {
        if (domainStatus == null) return null;
        return MessageDeliveryEntity.DeliveryStatus.valueOf(domainStatus.name());
    }

    private MessageDelivery.DeliveryChannel mapToDomainChannel(MessageDeliveryEntity.DeliveryChannel entityChannel) {
        if (entityChannel == null) return null;
        return MessageDelivery.DeliveryChannel.valueOf(entityChannel.name());
    }

    private MessageDeliveryEntity.DeliveryChannel mapToEntityChannel(MessageDelivery.DeliveryChannel domainChannel) {
        if (domainChannel == null) return null;
        return MessageDeliveryEntity.DeliveryChannel.valueOf(domainChannel.name());
    }
}
