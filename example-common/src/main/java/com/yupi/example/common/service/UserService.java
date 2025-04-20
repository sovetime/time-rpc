package com.yupi.example.common.service;

import com.yupi.example.common.model.User;

//公共模块接口
public interface UserService {

    //获取用户
    User getUser(User user);

    //测试 mock 接口返回值
    default short getNumber() {
        return 1;
    }
}
