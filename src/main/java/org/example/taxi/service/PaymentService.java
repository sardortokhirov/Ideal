package org.example.taxi.service;

import org.example.taxi.entity.Driver;
import org.example.taxi.entity.User;
import org.example.taxi.entity.Payment;
import org.example.taxi.repository.DriverRepository;
import org.example.taxi.repository.UserRepository;
import org.example.taxi.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service
public class PaymentService {

    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DriverRepository driverRepository;

    public void deductAppFee(Long driverId, Long orderId, int persons, String luggageType) {
        BigDecimal fee = BigDecimal.valueOf(persons * 20);
        if ("SEND_ALONE".equals(luggageType)) {
            fee = fee.add(BigDecimal.valueOf(10));
        }

        Payment payment = new Payment();
        payment.setDriverId(driverId);
        payment.setOrderId(orderId);
        payment.setAppFee(fee);
        paymentRepository.save(payment);

        Driver driver = driverRepository.findById(driverId).orElseThrow();
        if (driver.getWalletBalance() != null && driver.getWalletBalance().compareTo(fee) >= 0) {
            driver.setWalletBalance(driver.getWalletBalance().subtract(fee));
            driverRepository.save(driver);
        }
    }
}