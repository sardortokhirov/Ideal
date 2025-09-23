package org.example.taxi.repository;

import org.example.taxi.entity.OrderEntity;
import org.example.taxi.entity.OrderEntity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<OrderEntity> { // IMPORTANT: Implement JpaSpecificationExecutor
    // --- Existing Queries ---
    List<OrderEntity> findByStatusAndToDistrictId(OrderStatus status, Long toDistrictId);
    List<OrderEntity> findByStatusAndPickupTimeBetween(OrderStatus status, LocalDateTime start, LocalDateTime end);
    List<OrderEntity> findByStatusAndSeatsLessThanEqual(OrderStatus status, int seats);
    @Query("SELECT o FROM OrderEntity o WHERE o.status <> 'COMPLETED' AND o.createdAt < ?1")
    List<OrderEntity> findIncompleteAfter7Hours(LocalDateTime time);
    @Query("SELECT o FROM OrderEntity o WHERE o.status = ?1 AND o.driverId IS NULL AND o.fromDistrictId = ?2 AND (COALESCE(?5, '') = '' OR o.fromLocation LIKE %?5%) AND o.pickupTime BETWEEN ?3 AND ?4 ORDER BY o.pickupTime ASC")
    List<OrderEntity> findUnassignedOrdersInFromDistrictAndTime(OrderStatus status, Long fromDistrictId, LocalDateTime start, LocalDateTime end, String fromLocation);
    @Query("SELECT o FROM OrderEntity o " +
            "WHERE o.status = ?1 " +
            "AND o.driverId IS NULL " +
            "AND (o.toDistrictId IN ?2 OR o.fromDistrictId = ?3) " +
            "AND o.pickupTime BETWEEN ?4 AND ?5 " +
            "AND o.seats <= ?6 " +
            "ORDER BY o.pickupTime ASC")
    List<OrderEntity> findPendingOrdersForDriverFeed(
            OrderStatus status, List<Long> toDistrictIdsInRegion, Long driverDistrictId, LocalDateTime start, LocalDateTime end, int maxSeats);

    // --- Order History/Active (Client/Driver) ---
    List<OrderEntity> findByUserIdOrderByPickupTimeDesc(Long userId);
    List<OrderEntity> findByUserIdAndStatusOrderByPickupTimeDesc(Long userId, OrderStatus status);
    Optional<OrderEntity> findByUserIdAndStatusIn(Long userId, List<OrderStatus> statuses);
    List<OrderEntity> findByDriverIdOrderByPickupTimeDesc(Long driverId);
    List<OrderEntity> findByDriverIdAndStatusOrderByPickupTimeDesc(Long driverId, OrderStatus status);
    List<OrderEntity> findByDriverIdAndStatusIn(Long driverId, List<OrderStatus> statuses);

    // --- Operator-specific views ---
    List<OrderEntity> findByStatusAndDriverIdIsNull(OrderStatus status);
    List<OrderEntity> findByStatusIn(List<OrderStatus> statuses);

    // --- New: For Admin Analytics ---
    long count(); // Explicitly defined
    long countByStatus(OrderStatus status);
    long countByStatusIn(List<OrderStatus> statuses);
    List<OrderEntity> findByStatus(OrderStatus status); // To fetch all completed orders for aggregation
    List<OrderEntity> findByStatusAndCreatedAtAfter(OrderStatus status, LocalDateTime createdAt);
}