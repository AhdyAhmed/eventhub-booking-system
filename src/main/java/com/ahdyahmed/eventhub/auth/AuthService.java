package com.ahdyahmed.eventhub.auth;

import com.ahdyahmed.eventhub.auth.dto.AuthResponse;
import com.ahdyahmed.eventhub.auth.dto.LoginRequest;
import com.ahdyahmed.eventhub.auth.dto.RegisterRequest;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

}
