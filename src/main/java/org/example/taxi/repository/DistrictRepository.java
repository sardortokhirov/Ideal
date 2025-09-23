package org.example.taxi.repository;

import org.example.taxi.entity.District;
import org.example.taxi.entity.Region;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DistrictRepository extends JpaRepository<District, Long> {
    Optional<District> findByNameAndRegion(String name, Region region);
    Optional<District> findByNameAndRegion_Id(String name, Long regionId);
    List<District> findByRegion(Region region);
    List<District> findByRegion_Name(String regionName);
}