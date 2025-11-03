package com.seatify.controller.auth;

import com.seatify.service.user.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Author: Lê Văn Nguyễn - CE181235
 * Description: Public OAuth2 redirect endpoint to match Google redirect URI at /oauth2/redirect
 */
@RestController
@RequiredArgsConstructor
public class OAuth2CallbackController {

    private final AuthService authService;

    @Operation(summary = "Public OAuth2 redirect endpoint for Google")
    @GetMapping("/oauth2/redirect")
    public ResponseEntity<?> handlePublicGoogleRedirect(@RequestParam("code") String code,
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


