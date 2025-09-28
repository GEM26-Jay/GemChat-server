package com.zcj.serviceuser.controller;

import com.zcj.common.context.UserContext;
import com.zcj.common.dto.LoginDTO;
import com.zcj.common.dto.UserDTO;
import com.zcj.common.entity.User;
import com.zcj.serviceuser.service.UserService;
import com.zcj.common.vo.Result;
import com.zcj.common.vo.UserProfileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user")
@Tag(name = "用户管理接口")
@Slf4j
public class UserController {

    @Autowired
    private UserService userService;

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<String> login(@RequestBody LoginDTO loginDTO, HttpServletRequest request) {
        Result<String> result =  userService.login(loginDTO, getClientIp(request));
        log.info("用户登录：{}, 返回：{}", loginDTO, result);
        return result;
    }

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<Void> register(@RequestBody UserDTO user) {
        Result<Void> result = userService.register(user);
        log.info("用户注册：{}, 返回：{}", user, result);
        return result;
    }

    @Operation(summary = "获取用户信息")
    @GetMapping("/info")
    public Result<UserProfileVO> getUserInfo(@RequestParam(required = false) Long userId) {
        if (userId == null) {
            userId = UserContext.getId();
        }
        return userService.getUserById(userId);
    }

    @Operation(summary = "批量获取用户信息")
    @PostMapping("/infoBatch")
    public Result<List<UserProfileVO>> getUserInfoBatch(@RequestBody List<Long> userIds) {
        return userService.getBatchByIds(userIds);
    }

    @Operation(summary = "更新用户信息")
    @PutMapping
    public Result<Void> updateUser(@RequestBody User user) {
        return userService.updateUser(user);
    }

    @Operation(summary = "注销用户")
    @DeleteMapping("/")
    public Result<Void> deleteUser() {
        return userService.deleteUser();
    }

    @Operation(summary = "搜索用户")
    @GetMapping("/search")
    public Result<List<UserProfileVO>> searchUser(@RequestParam String keyword) {
        return userService.searchUser(keyword);
    }


    public String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null) {
            // 如果有多个IP，取第一个
            return xfHeader.split(",")[0];
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null) {
            return realIp;
        }
        return request.getRemoteAddr();
    }
}
