package com.hznan.mamgareader.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hznan.mamgareader.exception.BusinessException;
import com.hznan.mamgareader.model.dto.ChangePasswordRequest;
import com.hznan.mamgareader.model.dto.LoginRequest;
import com.hznan.mamgareader.model.dto.RegisterRequest;
import com.hznan.mamgareader.model.entity.User;
import com.hznan.mamgareader.model.vo.AuthResponse;
import com.hznan.mamgareader.model.vo.UserVO;
import com.hznan.mamgareader.mapper.UserMapper;
import com.hznan.mamgareader.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        boolean exists = userMapper.exists(
                new LambdaQueryWrapper<User>().eq(User::getUsername, req.username()));
        if (exists) {
            throw new BusinessException("用户名已存在");
        }

        String hash = BCrypt.withDefaults().hashToString(12, req.password().toCharArray());

        User user = User.builder()
                .username(req.username())
                .passwordHash(hash)
                .build();
        userMapper.insert(user);

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return new AuthResponse(token, toVO(user));
    }

    public AuthResponse login(LoginRequest req) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, req.username()));
        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }

        BCrypt.Result result = BCrypt.verifyer().verify(req.password().toCharArray(), user.getPasswordHash());
        if (!result.verified) {
            throw new BusinessException("用户名或密码错误");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return new AuthResponse(token, toVO(user));
    }

    public UserVO getMe(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(401, "用户不存在");
        return toVO(user);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest req) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");

        BCrypt.Result result = BCrypt.verifyer().verify(req.oldPassword().toCharArray(), user.getPasswordHash());
        if (!result.verified) {
            throw new BusinessException("旧密码错误");
        }

        String newHash = BCrypt.withDefaults().hashToString(12, req.newPassword().toCharArray());
        user.setPasswordHash(newHash);
        userMapper.updateById(user);
    }

    private UserVO toVO(User user) {
        return new UserVO(user.getId(), user.getUsername(), user.getAvatarUrl());
    }
}
