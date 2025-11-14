package com.zcj.serviceuser.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zcj.common.context.UserContext;
import com.zcj.common.dto.LoginDTO;
import com.zcj.common.dto.UserDTO;
import com.zcj.common.entity.User;
import com.zcj.common.entity.UserLogin;
import com.zcj.common.feign.FileServiceFeignClient;
import com.zcj.serviceuser.mapper.UserLoginMapper;
import com.zcj.serviceuser.mapper.UserMapper;
import com.zcj.serviceuser.service.UserService;
import com.zcj.common.utils.JWTUtil;
import com.zcj.common.utils.SnowflakeIdGenerator;
import com.zcj.common.vo.Result;
import com.zcj.common.vo.UserProfileVO;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import cn.hutool.core.lang.Validator;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    final private JWTUtil jwtUtil;
    final private UserMapper userMapper;
    final private SnowflakeIdGenerator snowflakeIdGenerator;
    final private UserLoginMapper userLoginMapper;
    final private ObjectMapper objectMapper;
    final private FileServiceFeignClient fileServiceFeignClient;

    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    @Transactional
    public Result<String> login(LoginDTO loginDTO, String clientIp) {

        if (!verifyAccountExist(loginDTO.getAccount())) {
            // 布隆过滤器
            return Result.error("账号不存在");
        }

        // 查询用户
        User user = getUserByAccount(loginDTO.getAccount());
        if (user == null) {
            return Result.error("账号或密码错误"); // 模糊提示，避免暴露账号存在信息
        }

        UserLogin userLogin = new UserLogin();
        BeanUtils.copyProperties(user, userLogin);
        userLogin.setId(snowflakeIdGenerator.nextId());
        userLogin.setUserId(user.getId());
        userLogin.setPlatform(loginDTO.getPlatform());
        userLogin.setDeviceHash(loginDTO.getDeviceHash());
        userLogin.setCreatedAt(System.currentTimeMillis());
        userLogin.setLoginIp(clientIp);

        // 密码验证（使用matches方法）
        if (!encoder.matches(loginDTO.getPassword(), user.getPasswordHash())) {
            userLogin.setStatus(UserLogin.TYPE_FAILED);
            userLogin.setRemark("账号或密码错误");
            return Result.error("账号或密码错误");
        }

        // 生成JWT
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        String jwt = jwtUtil.create(claims);

        userLogin.setStatus(UserLogin.TYPE_SUCCESS);
        userLoginMapper.insert(userLogin);
        return Result.success(jwt);
    }

    @Override
    @Transactional
    public Result<Void> register(UserDTO userDTO) {
        // 参数校验
        if (userDTO == null) {
            return Result.error("用户信息不能为空");
        }

        // 检查账号是否已存在
        if (userDTO.getPhone() != null &&
                !userDTO.getPhone().isEmpty() &&
                getUserByAccount(userDTO.getPhone()) != null) {
            return Result.error("电话已注册");
        }
        if (userDTO.getEmail()!=null &&
                !userDTO.getEmail().isEmpty() &&
                getUserByAccount(userDTO.getEmail()) != null) {
            return Result.error("邮箱已注册");
        }

        User user = new User();
        BeanUtils.copyProperties(userDTO, user);
        long id = snowflakeIdGenerator.nextId();
        user.setId(id);
        Long time = System.currentTimeMillis();
        user.setCreatedAt(time);
        user.setUpdatedAt(time);

        // 密码加密
        user.setPasswordHash(encoder.encode(userDTO.getPassword()));
        if (user.getAvatar() == null || user.getAvatar().isEmpty()) {
            user.setAvatar("default_avatar.png");
        }

        // 保存用户
        try {
            userMapper.insert(user);
            fileServiceFeignClient.addRef(user.getAvatar(), user.getId());
            return Result.success();
        } catch (Exception e) {
            log.error("用户注册失败", e);
            return Result.error("注册失败，请稍后重试");
        }
    }

    @Override
    public Result<UserProfileVO> getUserById(Long userId) {
        User user = userMapper.selectById(userId);
        UserProfileVO userProfileVO = new UserProfileVO();
        try {
            String json = objectMapper.writeValueAsString(user);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        if (user != null) {
            BeanUtils.copyProperties(user, userProfileVO);
            return Result.success(userProfileVO);
        } else {
            return Result.error("用户不存在");
        }

    }

    @Override
    public Result<List<UserProfileVO>> getBatchByIds(List<Long> userIds) {
        // 1. 参数校验
        if (userIds == null || userIds.isEmpty()) {
            return Result.success(Collections.emptyList()); // 返回空列表而不是null
        }

        // 2. 数据库查询（根据用户ID列表查询用户信息）
        List<User> userList = userMapper.selectList(
                new QueryWrapper<User>().in("id", userIds) // 明确泛型类型，指定数据库字段名
        );

        // 3. 转换为VO（如果需要将实体类转换为视图对象）
        List<UserProfileVO> voList = userList.stream()
                .map(user -> {
                    UserProfileVO userProfileVO = new UserProfileVO();
                    BeanUtils.copyProperties(user, userProfileVO);
                    // 其他属性...
                    return userProfileVO;
                })
                .collect(Collectors.toList());

        // 4. 返回成功结果
        return Result.success(voList);
    }

    @Override
    @Transactional
    public Result<Void> updateUser(User user) {
        user.setUpdatedAt(System.currentTimeMillis());
        userMapper.updateById(user);
        return Result.success();
    }

    public Result<Void> sendVerifiedCode(String account){

        if (!verifyAccountExist(account)){
            return Result.error("账号不存在");
        }

        User user = getUserByAccount(account);
        if (user == null){
            // 账号不存在
            return Result.error("账号不存在");
        }else if (user.getStatus() == User.STATUS_DISABLED) {
            // 账号被禁用
            return Result.error("账号被禁用");
        }else if (user.getStatus() == User.STATUS_FROZEN){
            // 账号被冻结
            return Result.error("账号被冻结");
        }else if (user.getStatus() == User.STATUS_NORMAL){
            // 账号正常
            if (Validator.isEmail(account)){
                sendEmailVerifiedCode(account);
            }else {
                sendPhoneVerifiedCode(account);
            }
            return Result.success();
        }
        return Result.error("未知错误");
    }

    @Override
    @Transactional
    public Result<Void> changePassword(String account, String verifiedCode, String newPassword) {
        User user = getUserByAccount(account);
        if (user != null && true){
            // todo: 验证verifiedCode是否正确
            user.setPasswordHash(encoder.encode(newPassword));
            userMapper.updateById(user);
            return Result.success();
        }else{
            return Result.error("验证码错误");
        }
    }

    @Override
    @Transactional
    public Result<Void> deleteUser() {
        Long id = UserContext.getId();
        User user = userMapper.selectById(id);
        user.setStatus(User.STATUS_DELETED);
        userMapper.updateById(user);
        return Result.success();
    }

    /**
     * 通过信息搜索用户：电话，邮箱，用户名
     * @param keyword: 关键词
     * @return Result<List<UserProfileVO>>
     */
    @Override
    public Result<List<UserProfileVO>> searchUser(String keyword){
        List<UserProfileVO> userVOS = new ArrayList<>();
        if (keyword != null && !keyword.isEmpty()){
            if (Validator.isEmail(keyword) || Validator.isMobile(keyword)){
                User user = getUserByAccount(keyword);
                if (user != null){
                    UserProfileVO userProfileVO = new UserProfileVO();
                    BeanUtils.copyProperties(user, userProfileVO);
                    userVOS.add(userProfileVO);
                }
            }else{
                // todo: 暂未实现
                userVOS.addAll(blurSearchByUserName(keyword));
            }
        }
        return Result.success(userVOS);
    }

    public List<UserProfileVO> blurSearchByUserName(String userName){
        return new ArrayList<UserProfileVO>();
    }

    /**
     * 验证账户是否存在
     * @param account 邮箱或者电话
     * @return boolean
     */
    public boolean verifyAccountExist(String account) {
        // todo: 布隆过滤器判断是否存在
        return true;
    }

    /**
     * 查找用户的相关信息，通过电话或者邮箱
     * @param account 电话或者邮箱
     * @return User
     */
    public User getUserByAccount(String account) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (Validator.isEmail(account)) {
            wrapper.eq(User::getEmail, account);
        }else if (Validator.isMobile(account)) {
            wrapper.eq(User::getPhone, account);
        }else {
            return null;
        }
        wrapper.ne(User::getStatus, User.STATUS_DELETED);
        return userMapper.selectOne(wrapper);
    }

    public void sendPhoneVerifiedCode(String phone){

    }

    public void sendEmailVerifiedCode(String Email){

    }
}
