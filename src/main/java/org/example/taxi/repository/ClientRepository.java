package org.example.taxi.repository;

import org.example.taxi.entity.Client;
import org.example.taxi.entity.Client.ClientOrderSource;
import org.example.taxi.entity.District;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {
    Optional<Client> findByUser_Id(Long userId);

    List<Client> findAllByOrderByUser_CreatedAtDesc(Pageable pageable);
    long count();

    List<Client> findByDistrict(District district);
    long countByDistrict(District district);

    List<Client> findByCreatedAtAfter(LocalDateTime createdAt);
    List<Client> findByOrderSourceAndCreatedAtAfter(ClientOrderSource orderSource, LocalDateTime createdAt);
}