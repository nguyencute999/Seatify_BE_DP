package com.seatify.controller.auth;

import com.seatify.dto.auth.*;
import com.seatify.service.user.AuthService;
import com.seatify.service.user.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * Author: Lê Văn Nguyễn - CE181235
 * Description: Controller xử lý các chức năng xác thực người dùng (Authentication)
 * Bao gồm: đăng nhập, đăng ký, đăng xuất, quên mật khẩu, reset mật khẩu và đăng nhập Google.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;//xử lý login, register và auth
    private final PasswordResetService passwordResetService;//xử lý cấp lại mk, otp

    /**
     * Đăng nhập tài khoản
     *
     * @param formLogin Dữ liệu đăng nhập gồm email và mật khẩu
     * @return Thông tin phản hồi chứa token đăng nhập (JWT) hoặc lỗi
     */
    @Operation(summary = "Đăng nhập tài khoản")
    @PostMapping("/login")
    public ResponseEntity<?> handleLogin(@Valid @RequestBody FormLogin formLogin) {
        var loginResponse = authService.login(formLogin);
        return ResponseEntity.ok(loginResponse);
    }

    /**
     * Đăng ký tài khoản mới
     *
     * @param formRegister Thông tin đăng ký gồm email, tên, mật khẩu,...
     * @return Thông báo đăng ký thành công
     */
    @Operation(summary = "Đăng ký tài khoản")
    @PostMapping("/register")
    public ResponseEntity<?> handleRegister(@Valid @RequestBody FormRegister formRegister) {
        authService.register(formRegister);
        return ResponseEntity.created(URI.create("api/v1/auth/register")).body(
                ResponseWrapper.builder()
                        .status(HttpStatus.CREATED).code(201)
                        .data("Đăng ký tài khoản thành công").build());
    }
    /**
     * Đăng xuất tài khoản
     *
     * @return Thông báo đăng xuất thành công
     */
    @Operation(summary = "Đăng xuất tài khoản")
    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        return ResponseEntity.ok("Đăng xuất thành công.");
    }

//    @Operation(summary = "Quên mật khẩu")
//    @PostMapping("/forgot-password")
//    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest request) {
//        passwordResetService.processForgotPassword(request);
//        return ResponseEntity.ok("Vui lòng kiểm tra email để đặt lại mật khẩu.");
//    }

    /**
     * Gửi mã OTP để đặt lại mật khẩu (quên mật khẩu)
     *
     * @param request Chứa email của người dùng cần đặt lại mật khẩu
     * @return Thông báo đã gửi OTP qua email
     */
    @Operation(summary = "Quên mật khẩu - gửi OTP qua email")
    @PostMapping("/forgot-password/otp")
    public ResponseEntity<?> forgotPasswordOtp(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.processForgotPasswordWithOtp(request);
        return ResponseEntity.ok("Đã gửi OTP tới email. Vui lòng kiểm tra hộp thư.");
    }

//    @Operation(summary = "Đặt lại mật khẩu")
//    @PostMapping("/reset-password")
//    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
//        passwordResetService.resetPassword(request);
//        return ResponseEntity.ok("Mật khẩu đã được cập nhật thành công.");
//    }

    /**
     * Đặt lại mật khẩu bằng OTP
     *
     * @param request Dữ liệu gồm email, OTP và mật khẩu mới
     * @return Thông báo đặt lại mật khẩu thành công
     */
    @Operation(summary = "Đặt lại mật khẩu bằng OTP")
    @PostMapping("/reset-password/otp")
    public ResponseEntity<?> resetPasswordWithOtp(@Valid @RequestBody ResetPasswordWithOtpRequest request) {
        passwordResetService.resetPasswordWithOtp(request);
        return ResponseEntity.ok("Mật khẩu đã được cập nhật thành công.");
    }


    /**
     * Tạo URL chuyển hướng sang trang đăng nhập Google
     *
     * @param request Thông tin HTTP request hiện tại (để xác định URL)
     * @return URL để frontend redirect sang Google login
     */
    @GetMapping("/google-login")
    public ResponseEntity<?> redirectToGoogle(HttpServletRequest request) {
        String redirectUrl = authService.getGoogleRedirectUrl(request);
        return ResponseEntity.ok(ResponseWrapper.builder()
                .status(HttpStatus.OK).code(200)
                .message("API login Google")
                .data(new AuthRedirectResponse(redirectUrl)).build());
    }

    /**
     * Nhận mã code từ Google (sau khi người dùng xác thực) và đổi thành JWT
     *
     * @param request Chứa code và redirectUri nhận từ Google
     * @return JWT token và thông tin người dùng
     */
    @Operation(summary = "Nhận mã code từ Google và trả về JWT")
    @PostMapping("/google/code")
    public ResponseEntity<?> handleGoogleCode(@Valid @RequestBody OAuth2CodeRequestDTO request) {
        try {
            var response = authService.exchangeGoogleCodeForToken(request.code(), request.redirectUri());
            return ResponseEntity.ok(ResponseWrapper.builder()
                    .status(HttpStatus.OK).code(200)
                    .message("Đăng nhập Google thành công").data(response).build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ResponseWrapper.builder()
                    .status(HttpStatus.BAD_REQUEST).code(400)
                    .message("Đăng nhập Google thất bại: " + e.getMessage()).build());
        }
    }

    /**
     * Callback endpoint được Google gọi lại sau khi người dùng đăng nhập Google thành công
     *
     * @param code  Mã xác thực do Google cung cấp
     * @param state (tuỳ chọn) giá trị trạng thái được gửi kèm (nếu có)
     * @return Redirect đến frontend kèm token hoặc thông báo lỗi
     */
    @Operation(summary = "Callback endpoint cho Google OAuth2")
    @GetMapping("/oauth2/callback/google")
    public ResponseEntity<?> handleGoogleCallback(@RequestParam("code") String code, 
                                                  @RequestParam(value = "state", required = false) String state) {
        try {
            String redirectUri = "https://www.seatify.com.vn/oauth2/redirect";
            var response = authService.exchangeGoogleCodeForToken(code, redirectUri);
            
            String frontendUrl = "https://www.seatify.com.vn";
            String redirectUrl = frontendUrl + "/oauth2/redirect?token=" + response.accessToken + "&roles=" + String.join(",", response.roles);
            
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", redirectUrl)
                    .build();
        } catch (Exception e) {
            String frontendUrl = "https://www.seatify.com.vn";
            String errorUrl = frontendUrl + "/oauth2/redirect?error=login_failed&message=" + e.getMessage();
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", errorUrl)
                    .build();
        }
    }
}


