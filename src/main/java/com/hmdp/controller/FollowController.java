package com.hmdp.controller;


import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import javax.annotation.Resource;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;



/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Resource
    IFollowService followService;

    @PutMapping("/{id}/{follow}")
    public Result followUser(@PathVariable(name = "id") Long followId, @PathVariable(name = "follow") Boolean follow) {
        return followService.followUser(followId, follow);
    }

    @GetMapping("/common/{id}")
    public Result getCommonFollows(@PathVariable(name = "id") Long userId) {
        return followService.commonFollow(userId);
    }
    
    @GetMapping("/or/not/{id}")
    public Result isFollowed(@PathVariable Long id) {
        return followService.isFollowed(id);
    }
}
