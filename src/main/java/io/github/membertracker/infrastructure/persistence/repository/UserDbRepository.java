package io.github.membertracker.infrastructure.persistence.repository;

import io.github.membertracker.domain.model.User;
import io.github.membertracker.domain.repository.UserRepository;
import io.github.membertracker.domain.valueobject.Email;
import io.github.membertracker.infrastructure.persistence.entity.UserEntity;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserDbRepository implements UserRepository {

    private final UserJpaRepository userJpaRepository;

    public UserDbRepository(UserJpaRepository userJpaRepository) {
        this.userJpaRepository = userJpaRepository;
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userJpaRepository.findByEmail(email)
                .map(this::toDomain);
    }

    @Override
    public Optional<User> findById(Long id) {
        return userJpaRepository.findById(id)
                .map(this::toDomain);
    }

    @Override
    public User save(User user) {
        UserEntity entity = toEntity(user);
        UserEntity savedEntity = userJpaRepository.save(entity);
        return toDomain(savedEntity);
    }

    @Override
    public User update(User user) {
        UserEntity entity = toEntity(user);
        UserEntity updatedEntity = userJpaRepository.save(entity);
        return toDomain(updatedEntity);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userJpaRepository.existsByEmail(email);
    }

    private User toDomain(UserEntity entity) {
        User user = new User();
        user.setId(entity.getId());
        user.setEmail(Email.of(entity.getEmail()));
        user.setPassword(entity.getPassword());
        user.setRole(entity.getRole());
        user.setEnabled(entity.isEnabled());
        user.setAccountNonLocked(entity.isAccountNonLocked());
        user.setCredentialsNonExpired(entity.isCredentialsNonExpired());
        user.setLastPasswordChange(entity.getLastPasswordChange());
        user.setFailedLoginAttempts(entity.getFailedLoginAttempts());
        user.setCreatedAt(entity.getCreatedAt());
        user.setUpdatedAt(entity.getUpdatedAt());
        user.setFirstName(entity.getFirstName());
        user.setLastName(entity.getLastName());
        user.setPhone(entity.getPhone());
        user.setBio(entity.getBio());
        return user;
    }

    private UserEntity toEntity(User user) {
        UserEntity entity = new UserEntity();
        entity.setId(user.getId());
        entity.setEmail(user.getEmail().getValue());
        entity.setPassword(user.getPassword());
        entity.setRole(user.getRole());
        entity.setEnabled(user.isEnabled());
        entity.setAccountNonLocked(user.isAccountNonLocked());
        entity.setCredentialsNonExpired(user.isCredentialsNonExpired());
        entity.setLastPasswordChange(user.getLastPasswordChange());
        entity.setFailedLoginAttempts(user.getFailedLoginAttempts());
        entity.setCreatedAt(user.getCreatedAt());
        entity.setUpdatedAt(user.getUpdatedAt());
        entity.setFirstName(user.getFirstName());
        entity.setLastName(user.getLastName());
        entity.setPhone(user.getPhone());
        entity.setBio(user.getBio());
        return entity;
    }
}