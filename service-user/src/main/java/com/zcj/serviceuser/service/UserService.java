package com.zcj.serviceuser.service;


import com.zcj.common.dto.LoginDTO;
import com.zcj.common.dto.UserDTO;
import com.zcj.common.entity.User;
import com.zcj.common.vo.Result;
import com.zcj.common.vo.UserProfileVO;

import java.util.List;

public interface UserService {

    /**
     * 用户登录
     * @return JWT令牌
     */
    Result<String> login(LoginDTO loginDTO, String clientIp);

    /**
     * 用户注册
     * @param user 用户信息
     */
    Result<Void> register(UserDTO user);

    /**
     * 根据ID获取用户信息
     * @param userId 用户ID
     * @return 用户信息
     */
    Result<UserProfileVO> getUserById(Long userId);

    /**
     * 更新用户信息
     * @param user 用户信息
     */
    Result<Void> updateUser(User user);

    Result<Void> changePassword(String account, String verifiedCode, String newPassword);

    /**
     * 注销用户
     */
    Result<Void> deleteUser();

    Result<List<UserProfileVO>> searchUser(String keyword);

    Result<List<UserProfileVO>> getBatchByIds(List<Long> userIds);
}
