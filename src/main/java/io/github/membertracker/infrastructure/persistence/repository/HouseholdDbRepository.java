package io.github.membertracker.infrastructure.persistence.repository;

import io.github.membertracker.domain.model.Household;
import io.github.membertracker.domain.model.HouseholdSummary;
import io.github.membertracker.domain.model.Member;
import io.github.membertracker.domain.repository.HouseholdRepository;
import io.github.membertracker.infrastructure.persistence.entity.HouseholdEntity;
import io.github.membertracker.infrastructure.persistence.mapper.MemberPersistenceMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class HouseholdDbRepository implements HouseholdRepository {

    private final HouseholdJpaRepository householdJpaRepository;
    private final MemberJpaRepository memberJpaRepository;
    private final PersonJpaRepository personJpaRepository;

    public HouseholdDbRepository(HouseholdJpaRepository householdJpaRepository,
                                 MemberJpaRepository memberJpaRepository,
                                 PersonJpaRepository personJpaRepository) {
        this.householdJpaRepository = householdJpaRepository;
        this.memberJpaRepository = memberJpaRepository;
        this.personJpaRepository = personJpaRepository;
    }

    @Override
    public List<HouseholdSummary> findAllSummaries() {
        return householdJpaRepository.findAllSummaries();
    }

    @Override
    public Optional<Household> findById(Long id) {
        return householdJpaRepository.findById(id).map(HouseholdDbRepository::toDomain);
    }

    @Override
    public List<Member> findMembers(Long householdId) {
        return memberJpaRepository.findByPersonHouseholdIdOrderByPersonNameAscIdAsc(householdId).stream()
                .map(MemberPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public long countPeople(Long householdId) {
        return personJpaRepository.countByHouseholdId(householdId);
    }

    @Override
    public Household save(Household household) {
        return toDomain(householdJpaRepository.save(toEntity(household)));
    }

    @Override
    public void deleteById(Long id) {
        householdJpaRepository.deleteById(id);
    }

    private static Household toDomain(HouseholdEntity entity) {
        Household household = new Household(entity.getName(), entity.getAddressLine1(), entity.getAddressLine2(),
                entity.getCity(), entity.getPostalCode(), entity.getNotes());
        household.setId(entity.getId());
        return household;
    }

    private static HouseholdEntity toEntity(Household household) {
        HouseholdEntity entity = new HouseholdEntity();
        entity.setId(household.getId());
        entity.setName(household.getName());
        entity.setAddressLine1(household.getAddressLine1());
        entity.setAddressLine2(household.getAddressLine2());
        entity.setCity(household.getCity());
        entity.setPostalCode(household.getPostalCode());
        entity.setNotes(household.getNotes());
        return entity;
    }
}
