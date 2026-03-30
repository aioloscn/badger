package com.aiolos.badger.identitycore.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountTokenDTO implements Serializable {
    private String accessToken;
    private String refreshToken;
}
