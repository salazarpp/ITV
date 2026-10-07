package com.pokesync.application.port.out;

import com.pokesync.application.dto.AuthToken;
import java.util.UUID;

public interface TokenIssuer {
    AuthToken issue(UUID userId);
}
