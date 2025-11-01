package com.seatify.service.user;

import com.seatify.dto.auth.ForgotPasswordRequest;
import com.seatify.dto.auth.ResetPasswordRequest;
import com.seatify.dto.auth.ResetPasswordWithOtpRequest;
import com.seatify.exception.ResourceNotFoundException;
import com.seatify.exception.ValidationException;
import com.seatify.repository.UserRepository;
import com.seatify.security.JwtTokenProvider;
import com.seatify.model.PasswordResetOtp;
import com.seatify.repository.PasswordResetOtpRepository;
import com.seatify.service.user.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;

/**
 * @author : Lê Văn Nguyễn - CE181235
 */
@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {

    private final UserRepository userRepo;
    private final JwtTokenProvider jwt;
    private final PasswordEncoder encoder;
    private final PasswordResetOtpRepository otpRepo;
    private final EmailService emailService;

    @Override
    public void processForgotPassword(ForgotPasswordRequest request) {
        userRepo.findByEmail(request.email()).ifPresent(user -> {
            String token = jwt.generateToken(user.getEmail(), Map.of("reset", true));
            //Gửi email thật
            System.out.println("RESET LINK: https://www.seatify.com.vn/reset?token=" + token);
        });
    }

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        String email = jwt.getSubject(request.token());
        var user = userRepo.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User không tồn tại"));
        user.setPasswordHash(encoder.encode(request.newPassword()));
        userRepo.save(user);
    }

    @Override
    public void processForgotPasswordWithOtp(ForgotPasswordRequest request) {
        userRepo.findByEmail(request.email()).ifPresent(user -> {
            String otp = String.format("%06d", new Random().nextInt(1_000_000));
            Instant expires = Instant.now().plus(Duration.ofMinutes(10));
            PasswordResetOtp entity = PasswordResetOtp.builder()
                    .email(user.getEmail())
                    .code(otp)
                    .expiresAt(expires)
                    .used(false)
                    .build();
            otpRepo.save(entity);
            emailService.sendOtpEmail(user.getEmail(), user.getFullName(), otp);
        });
    }

    @Override
    public void resetPasswordWithOtp(ResetPasswordWithOtpRequest request) {
        // Kiểm tra mật khẩu mới và xác nhận mật khẩu có khớp nhau không
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new ValidationException("Mật khẩu mới và xác nhận mật khẩu không khớp");
        }
        
        // Kiểm tra độ dài mật khẩu tối thiểu
        if (request.newPassword().length() < 6) {
            throw new ValidationException("Mật khẩu phải có ít nhất 6 ký tự");
        }
        
        var latest = otpRepo.findTopByEmailAndUsedOrderByIdDesc(request.email(), false)
                .orElseThrow(() -> new ResourceNotFoundException("OTP không tồn tại"));
        if (latest.isUsed() || Instant.now().isAfter(latest.getExpiresAt()) || !latest.getCode().equals(request.otp())) {
            throw new ValidationException("OTP không hợp lệ hoặc đã hết hạn");
        }
        var user = userRepo.findByEmail(request.email()).orElseThrow(() -> new ResourceNotFoundException("User không tồn tại"));
        user.setPasswordHash(encoder.encode(request.newPassword()));
        userRepo.save(user);
        latest.setUsed(true);
        otpRepo.save(latest);
    }
}


