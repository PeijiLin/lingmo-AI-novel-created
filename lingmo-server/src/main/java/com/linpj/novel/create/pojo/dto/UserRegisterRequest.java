package com.linpj.novel.create.pojo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * @author HL
 */
@Data
public class UserRegisterRequest implements Serializable {
    @NotNull
    private String account;
    @NotNull
    private String password;
    @NotNull
    private String checkPassword;
}
