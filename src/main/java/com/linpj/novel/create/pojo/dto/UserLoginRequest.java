package com.linpj.novel.create.pojo.dto;

import lombok.Data;
import java.io.Serializable;


/**
 * @author HL
 */
@Data
public class UserLoginRequest implements Serializable {
    private String account;
    private String password;
}