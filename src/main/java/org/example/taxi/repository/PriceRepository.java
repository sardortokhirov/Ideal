package org.example.taxi.repository;

import org.example.taxi.entity.District;
import org.example.taxi.entity.Price;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PriceRepository extends JpaRepository<Price, Long> {
    Optional<Price> findByFromDistrictAndToDistrict(District fromDistrict, District toDistrict);
    List<Price> findByFromDistrict(District fromDistrict);
    List<Price> findByToDistrict(District toDistrict);
}