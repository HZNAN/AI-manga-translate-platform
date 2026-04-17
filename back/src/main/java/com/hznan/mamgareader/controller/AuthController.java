package com.hznan.mamgareader.controller;

import com.hznan.mamgareader.interceptor.AuthInterceptor;
import com.hznan.mamgareader.model.dto.ChangePasswordRequest;
import com.hznan.mamgareader.model.dto.LoginRequest;
import com.hznan.mamgareader.model.dto.RegisterRequest;
import com.hznan.mamgareader.model.vo.ApiResponse;
import com.hznan.mamgareader.model.vo.AuthResponse;
import com.hznan.mamgareader.model.vo.UserVO;
import com.hznan.mamgareader.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ApiResponse.ok(userService.register(req));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ApiResponse.ok(userService.login(req));
    }

    @GetMapping("/me")
    public ApiResponse<UserVO> me(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(AuthInterceptor.CURRENT_USER_ID);
        return ApiResponse.ok(userService.getMe(userId));
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(HttpServletRequest request,
                                             @Valid @RequestBody ChangePasswordRequest req) {
        Long userId = (Long) request.getAttribute(AuthInterceptor.CURRENT_USER_ID);
        userService.changePassword(userId, req);
        return ApiResponse.ok();
    }
}
