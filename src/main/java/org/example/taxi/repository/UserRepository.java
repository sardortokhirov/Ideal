package org.example.taxi.repository;

import org.example.taxi.entity.User;
import org.example.taxi.entity.User.UserType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByChatId(Long chatId);
    Optional<User> findByPhoneNumber(String phoneNumber);
    boolean existsByChatId(Long chatId);
    boolean existsByPhoneNumber(String phoneNumber);

    long countByUserType(UserType userType);
    long count();

    List<User> findByCreatedAtAfter(LocalDateTime createdAt);
    List<User> findByUserTypeAndCreatedAtAfter(UserType userType, LocalDateTime createdAt);

    Page<User> findByUserType(UserType userType, Pageable pageable);
}