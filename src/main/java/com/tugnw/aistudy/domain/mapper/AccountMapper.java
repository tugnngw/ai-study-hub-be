package com.tugnw.aistudy.domain.mapper;

import com.tugnw.aistudy.domain.dto.auth.AuthResponse;
import com.tugnw.aistudy.domain.entity.Account;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface AccountMapper {

    @Mapping(target = "userId", source = "id")
    @Mapping(target = "accessToken", ignore = true)
    @Mapping(target = "refreshToken", ignore = true)
    @Mapping(target = "expiresIn", ignore = true)
    AuthResponse toAuthResponse(Account account);

}