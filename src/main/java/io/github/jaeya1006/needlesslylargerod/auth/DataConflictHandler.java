package io.github.jaeya1006.needlesslylargerod.auth;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 외래 키·유니크 위반(탈퇴 직후 늦게 온 쓰기 등)을 500 대신 409로 */
@RestControllerAdvice
class DataConflictHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    void conflict() {
    }
}
