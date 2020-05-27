/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2020 Neil C Smith.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License version 3 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * version 3 for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License version 3
 * along with this work; if not, see http://www.gnu.org/licenses/
 * 
 *
 * Please visit https://www.praxislive.org if you need additional information or
 * have any questions.
 */
package org.praxislive.hub.net;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.praxislive.core.types.PMap;

/**
 *
 */
public final class HubConfiguration {
    
    private static final String KEY_FILESERVER = "enable-fileserver";
    private static final String KEY_PROXIES = "proxies";
    
    private final boolean fileServer;
    private final List<ProxyInfo> proxies;
    
    private HubConfiguration(Builder builder) {
        this.fileServer = builder.fileServer;
        this.proxies = List.copyOf(builder.proxies);
    }
    
    public boolean isFileServerEnabled() {
        return fileServer;
    }
    
    public List<ProxyInfo> proxies() {
        return proxies;
    }

    public static HubConfiguration fromMap(PMap configuration) {
        var builder = builder();
        builder.enableFileServer(configuration.getBoolean(KEY_FILESERVER, false));
        var value = configuration.get(KEY_PROXIES);
        if (value == null) {
            value = PMap.EMPTY;
        }
        PMap proxyMap = PMap.from(value).orElseThrow(IllegalArgumentException::new);
        proxyMap.keys().forEach(id -> 
            builder.proxy(DefaultProxyInfo.fromMap(
                    PMap.from(proxyMap.get(id))
                            .orElseThrow(IllegalArgumentException::new)
        )));
        return builder.build();
    }
    
    static Builder builder() {
        return new Builder();
    }
    
    static class Builder {
        
        private final List<ProxyInfo> proxies;
        private boolean fileServer;
        
        private Builder() {
            proxies = new ArrayList<>();
        }
        
        public Builder enableFileServer(boolean enable) {
            this.fileServer = enable;
            return this;
        }
        
        public Builder proxy(ProxyInfo proxy) {
            proxies.add(Objects.requireNonNull(proxy));
            return this;
        }
        
        public HubConfiguration build() {
            return new HubConfiguration(this);
        }
        
    }
    
    
    
}
