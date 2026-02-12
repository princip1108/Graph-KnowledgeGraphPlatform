package com.sdu.kgplatform.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
public class UserRegistrationDto {
    private String userName; 
    private String email;
    private String phone;
    private String password;
    private String verificationCode;

     private String role="USER";

}
