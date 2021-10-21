package com.sun.tingle.member.api.dto.request;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberResDto {
    private Long id;
    private String memberId;
    private String password;
    private String email;
    private String name;
    private String phone;
    private String auth;
} 
