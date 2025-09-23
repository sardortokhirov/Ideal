package org.example.taxi.service;

import org.example.taxi.entity.User;
import org.example.taxi.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import java.util.Collections;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private static final Logger logger = LoggerFactory.getLogger(CustomUserDetailsService.class);
    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String phoneNumber) throws UsernameNotFoundException {
        User user = userRepository.findByPhoneNumber(phoneNumber) // Use findByPhoneNumber
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + phoneNumber));

        if (user.getUserType() == null) {
            logger.error("User {} found but has no assigned UserType. Denying login.", phoneNumber);
            throw new UsernameNotFoundException("User " + phoneNumber + " has an invalid account setup.");
        }

        String role = "ROLE_" + user.getUserType().name(); // Correctly get the role from UserType
        logger.debug("Loading user: {}, Roles: {}", phoneNumber, role);
        return new org.springframework.security.core.userdetails.User(
                user.getPhoneNumber(),
                user.getPassword(), // This is the HASHED password from the database
                Collections.singletonList(new SimpleGrantedAuthority(role)));
    }
}