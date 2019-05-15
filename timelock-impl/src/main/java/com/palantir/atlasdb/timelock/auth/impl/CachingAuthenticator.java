/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.atlasdb.timelock.auth.impl;

import java.util.Map;
import java.util.Optional;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotAuthorizedException;

import org.immutables.value.Value;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.palantir.atlasdb.timelock.auth.api.Authenticator;
import com.palantir.atlasdb.timelock.auth.api.BCryptedSecret;
import com.palantir.atlasdb.timelock.auth.api.Client;
import com.palantir.atlasdb.timelock.auth.api.Password;

public class CachingAuthenticator implements Authenticator {
    private final Map<String, BCryptedSecret> credentials;

    private final LoadingCache<ClientCredentials, Optional<Client>> cache;

    CachingAuthenticator(Map<String, BCryptedSecret> credentials) {
        this.credentials = credentials;
        this.cache = Caffeine.newBuilder()
                .maximumSize(1000)
                .build(this::authenticateInternal);
    }

    public static Authenticator create(Map<String, BCryptedSecret> credentials) {
        return new CachingAuthenticator(credentials);
    }

    @Override
    public Client authenticate(String id, Password password) {
        return cache.get(ClientCredentials.of(id, password))
                .orElseThrow(ForbiddenException::new);
    }

    private Optional<Client> authenticateInternal(ClientCredentials clientCredentials) {
        BCryptedSecret secret = credentials.get(clientCredentials.id());
        if (secret == null) {
            return Optional.of(Client.ANONYMOUS);
        }
        return secret.check(clientCredentials.password())
                ? Optional.of(Client.create(clientCredentials.id()))
                : Optional.empty();
    }

    @Value.Immutable
    interface ClientCredentials {
        String id();
        Password password();

        static ClientCredentials of(String id, Password password) {
            return ImmutableClientCredentials.builder()
                    .id(id)
                    .password(password)
                    .build();
        }
    }
}