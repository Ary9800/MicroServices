package com.microservices.user.service.services;

import com.microservices.user.service.entities.Rating;
import com.microservices.user.service.entities.User;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.List;

public interface UserService {

    //user operations

    //create
    User saveUser(User user);

    //get all user
    List<User> getAllUser();

    //get single user of given userId

    User getUser(String userId);




}
