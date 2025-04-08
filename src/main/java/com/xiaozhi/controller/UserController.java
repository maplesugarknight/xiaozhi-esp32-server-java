package com.xiaozhi.controller;

import java.util.Map;

import javax.annotation.Resource;

import com.xiaozhi.common.exception.UserPasswordNotMatchException;
import com.xiaozhi.common.exception.UsernameNotFoundException;
import com.xiaozhi.common.web.AjaxResult;
import com.xiaozhi.common.web.SessionProvider;
import com.xiaozhi.entity.SysUser;
import com.xiaozhi.security.AuthenticationService;
import com.xiaozhi.service.SysDeviceService;
import com.xiaozhi.service.SysUserService;
import com.xiaozhi.utils.CmsUtils;
import com.xiaozhi.utils.ImageUtils;

import io.github.biezhi.ome.OhMyEmail;
import static io.github.biezhi.ome.OhMyEmail.SMTP_QQ;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * 用户信息
 * 
 * @author: Joey
 * 
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    @Resource
    private SysUserService userService;

    @Resource
    private SysDeviceService deviceService;

    @Resource
    private AuthenticationService authenticationService;

    @Resource
    private SessionProvider sessionProvider;

    /**
     * @param username
     * @param password
     * @return
     * @throws UsernameNotFoundException
     * @throws UserPasswordNotMatchException
     */
    @PostMapping("/login")
    public Mono<AjaxResult> login(@RequestBody Map<String, Object> loginRequest, ServerWebExchange exchange) {
        try {
            String username = (String) loginRequest.get("username");
            String password = (String) loginRequest.get("password");

            userService.login(username, password);
            SysUser user = userService.query(username);

            // 保存用户
            CmsUtils.setUser(exchange, user);

            return sessionProvider.setAttribute(exchange, SysUserService.USER_SESSIONKEY, user)
                    .thenReturn(AjaxResult.success(user));
        } catch (UsernameNotFoundException e) {
            return Mono.just(AjaxResult.error("用户不存在"));
        } catch (UserPasswordNotMatchException e) {
            return Mono.just(AjaxResult.error("密码错误"));
        } catch (Exception e) {
            log.info(e.getMessage(), e);
            return Mono.just(AjaxResult.error("操作失败"));
        }
    }

    /**
     * 新增用户
     * 
     * @param user
     * @return
     */
    @PostMapping("/add")
    public Mono<AjaxResult> add(@RequestBody Map<String, Object> loginRequest, ServerWebExchange exchange) {
        try {
            String username = (String) loginRequest.get("username");
            String email = (String) loginRequest.get("email");
            String password = (String) loginRequest.get("password");
            String code = (String) loginRequest.get("code");
            int row = userService.queryCaptcha(code, email);
            if (1 > row)
                return Mono.just(AjaxResult.error("无效验证码"));
            SysUser user = new SysUser();
            user.setUsername(username);
            user.setEmail(email);
            user.setPassword(password);
            String newPassword = authenticationService.encryptPassword(user.getPassword());
            user.setPassword(newPassword);
            if (0 < userService.add(user)) {
                return Mono.just(AjaxResult.success(user));
            }
            return Mono.just(AjaxResult.error());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return Mono.just(AjaxResult.error());
        }
    }

    /**
     * 用户信息查询
     * 
     * @param username
     * @return
     */
    @GetMapping("/query")
    public Mono<AjaxResult> query(String username) {
        try {
            SysUser user = userService.query(username);
            AjaxResult result = AjaxResult.success();
            result.put("data", user);
            return Mono.just(result);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return Mono.just(AjaxResult.error());
        }
    }

    /**
     * 用户信息修改
     *
     * @param user
     * @return
     */
    @PostMapping("/update")
    public Mono<AjaxResult> update(@RequestBody Map<String, Object> loginRequest, ServerWebExchange exchange) {
        try {
            String username = (String) loginRequest.get("username");
            String email = (String) loginRequest.get("email");
            String password = (String) loginRequest.get("password");
            SysUser userQuery = new SysUser();
            if (StringUtils.hasText(username)) {
                userQuery = userService.selectUserByUsername(username);
            } else if (StringUtils.hasText(email)) {
                userQuery = userService.selectUserByEmail(email);
            }
            if (ObjectUtils.isEmpty(userQuery)) {
                return Mono.just(AjaxResult.error("无此用户，操作失败"));
            }
            if (StringUtils.hasText(password)) {
                String newPassword = authenticationService.encryptPassword(password);
                userQuery.setPassword(newPassword);
            }
            if (!StringUtils.hasText(userQuery.getAvatar())) {
                userQuery.setAvatar(ImageUtils.GenerateImg(userQuery.getName()));
            }

            if (0 < userService.update(userQuery)) {
                return Mono.just(AjaxResult.success());
            }
            return Mono.just(AjaxResult.error());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return Mono.just(AjaxResult.error());
        }
    }

    /**
     * 邮箱验证码发送
     *
     * @param email
     * @return
     */
    @PostMapping("/sendEmailCaptcha")
    public Mono<AjaxResult> sendEmailCaptcha(@RequestBody(required = false) Map<String, Object> requestBody,
            ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            String email = (String) requestBody.get("email");
            String type = (String) requestBody.get("type");
            SysUser user = userService.selectUserByEmail(email);
            if (type.equals("forget") && ObjectUtils.isEmpty(user)) {
                return AjaxResult.error("该邮箱未注册");
            }
            SysUser code = userService.generateCode(new SysUser().setEmail(email));
            String emailContent = "尊敬的用户您好!您的验证码为:<h3>" + code.getCode() + "</h3>如不是您操作,请忽略此邮件.(有效期10分钟)";
            // 需要配置自己的第三方邮箱认证信息，这里用的QQ邮箱认证信息，需自己申请
            String username = "";
            String password = "";
            if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
                Mono.just(AjaxResult.error("未配置第三方邮箱认证信息,请联系管理员"));
            }
            OhMyEmail.config(SMTP_QQ(false), username, password);
            OhMyEmail.subject("小智ESP32-智能物联网管理平台").from("小智物联网管理平台").to(email).html(emailContent).send();

            return AjaxResult.success();
        }).onErrorResume(e -> {
            log.error(e.getMessage(), e);
            return Mono.just(AjaxResult.error("发送失败,请联系管理员"));
        });
    }

    /**
     * 验证验证码是否有效
     *
     * @param captcha
     * @param email
     * @return
     */
    @GetMapping("/checkCaptcha")
    public Mono<AjaxResult> checkCaptcha(String code, String email) {
        return Mono.fromCallable(() -> {
            int row = userService.queryCaptcha(code, email);
            if (1 > row)
                return AjaxResult.error("无效验证码");
            return AjaxResult.success();
        }).onErrorResume(e -> {
            log.error(e.getMessage(), e);
            return Mono.just(AjaxResult.error("操作失败,请联系管理员"));
        });
    }

    @GetMapping("/checkUser")
    public Mono<AjaxResult> checkUser(String username, String email) {
        return Mono.fromCallable(() -> {
            SysUser userName = userService.selectUserByUsername(username);
            SysUser userEmail = userService.selectUserByEmail(email);
            if (!ObjectUtils.isEmpty(userName)) {
                return AjaxResult.error("用户名已存在");
            } else if (!ObjectUtils.isEmpty(userEmail)) {
                return AjaxResult.error("邮箱已注册");
            }
            return AjaxResult.success();
        }).onErrorResume(e -> {
            log.error(e.getMessage(), e);
            return Mono.just(AjaxResult.error("操作失败,请联系管理员"));
        });
    }
}