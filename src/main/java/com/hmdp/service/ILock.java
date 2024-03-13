package com.hmdp.service;

public interface ILock {
    
    boolean tryLock(long timeOutSec);

    void unlock();
}