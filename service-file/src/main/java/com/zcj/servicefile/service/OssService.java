package com.zcj.servicefile.service;

public interface OssService {

    boolean isExist(String dir, String fileName);

    boolean delete(String dir, String fileName);
}
