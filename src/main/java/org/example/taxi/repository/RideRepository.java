package org.example.taxi.repository;

import org.example.taxi.entity.Ride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RideRepository extends JpaRepository<Ride, Long> {
    List<Ride> findByDriverIdAndStatus(Long driverId, String status);
    Optional<Ride> findByOrderId(Long orderId);
}