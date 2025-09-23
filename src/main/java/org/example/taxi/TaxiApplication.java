package org.example.taxi;

import org.example.taxi.entity.District; // Import District entity
import org.example.taxi.entity.Region;   // Import Region entity
import org.example.taxi.entity.User;
import org.example.taxi.repository.DistrictRepository; // Import DistrictRepository
import org.example.taxi.repository.RegionRepository;   // Import RegionRepository
import org.example.taxi.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional; // Import for @Transactional

import java.util.Optional;

@SpringBootApplication
public class TaxiApplication {

    private static final Logger logger = LoggerFactory.getLogger(TaxiApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(TaxiApplication.class, args);
    }

    @Bean
    @Transactional // Ensure data loading is atomic
    CommandLineRunner initializeData(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            RegionRepository regionRepository,    // Inject RegionRepository
            DistrictRepository districtRepository // Inject DistrictRepository
    ) {
        return args -> {
            // --- 1. Create Default Admin User (if not exists) ---
            String defaultAdminPhone = "+1234567890";
            Optional<User> existingAdminUser = userRepository.findByPhoneNumber(defaultAdminPhone);

            if (existingAdminUser.isEmpty()) {
                User adminUser = new User();
                adminUser.setPhoneNumber(defaultAdminPhone);
                adminUser.setFirstName("Super");   // Set first name
                adminUser.setLastName("Admin");    // Set last name
                String rawPassword = "password123";
                adminUser.setPassword(passwordEncoder.encode(rawPassword));
                adminUser.setUserType(User.UserType.ADMIN);
                userRepository.save(adminUser);

                User etamin = new User();
                etamin.setPhoneNumber("+1");
                etamin.setFirstName("Sardor");   // Set first name
                etamin.setLastName("Tokhirov");    // Set last name
                String etaminPassword = "1";
                etamin.setPassword(passwordEncoder.encode(etaminPassword));
                etamin.setUserType(User.UserType.ETAMIN);
                userRepository.save(etamin);

            } else {
                logger.info("Default ADMIN user with phone {} already exists.", defaultAdminPhone);
            }

            // --- 2. Load Initial Region and District Data (if not exists) ---
            if (regionRepository.count() == 0) {
                logger.info("Starting to load initial Region and District data...");

                // --- Region 1: Tashkent Region ---
                Region tashkentRegion = new Region(null, "Tashkent Region");
                tashkentRegion = regionRepository.save(tashkentRegion);
                logger.info("Created Region: {}", tashkentRegion.getName());

                districtRepository.save(new District(null, "Yunusabad District", tashkentRegion));
                districtRepository.save(new District(null, "Mirzo Ulugbek District", tashkentRegion));
                districtRepository.save(new District(null, "Chilonzor District", tashkentRegion));
                logger.info("Created Districts for Tashkent Region.");


                // --- Region 2: Namangan Region ---
                Region namanganRegion = new Region(null, "Namangan Region");
                namanganRegion = regionRepository.save(namanganRegion);
                logger.info("Created Region: {}", namanganRegion.getName());

                districtRepository.save(new District(null, "Namangan City", namanganRegion));
                districtRepository.save(new District(null, "Chust District", namanganRegion));
                districtRepository.save(new District(null, "Pop District", namanganRegion));
                logger.info("Created Districts for Namangan Region.");

                logger.info("Initial Region and District data loading complete.");
            } else {
                logger.info("Regions already exist in the database. Skipping initial data load.");
            }
        };
    }
}