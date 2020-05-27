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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.praxislive.core.ComponentType;
import org.praxislive.core.Value;
import org.praxislive.core.types.PArray;
import org.praxislive.core.types.PMap;

/**
 * Default implementation of {@link ProxyInfo} that can be parsed from a PMap,
 * used by {@link HubConfiguration}.
 */
public class DefaultProxyInfo implements ProxyInfo {

    private static final String KEY_HOST = "host";
    private static final String KEY_PORT = "port";
    private static final String KEY_TYPE_PATTERN = "type-pattern";
    private static final String KEY_ID_PATTERN = "id-pattern";
    private static final String KEY_EXEC = "exec";
    private static final String KEY_EXEC_COMMAND = "command";
    private static final String VALUE_EXEC_DEFAULT = "default";
    private static final String KEY_EXEC_OPTIONS = "java-options";
    private static final String KEY_EXEC_ARGS = "arguments";
    

    private final InetSocketAddress socketAddress;
    private final Pattern typePattern;
    private final Pattern idPattern;
    private final Exec exec;

    private DefaultProxyInfo(Builder builder) {
        this.socketAddress = builder.address;
        this.typePattern = builder.typeMatch;
        this.idPattern = builder.idMatch;
        this.exec = builder.exec;
    }

    @Override
    public InetSocketAddress socketAddress() {
        return socketAddress;
    }

    @Override
    public boolean matches(String rootID, ComponentType rootType) {
        if (!idPattern.matcher(rootID).matches()) {
            return false;
        }
        String tp = rootType.toString();
        if (!tp.startsWith("root:")) {
            return false;
        }
        tp = tp.substring(5);
        return typePattern.matcher(tp).matches();
    }

    @Override
    public Optional<ProxyInfo.Exec> exec() {
        return Optional.ofNullable(exec);
    }
    
    /**
     * Parse a DefaultProxyInfo from a provided PMap.
     * 
     * @param conf proxy info as PMap
     * @return DefaultProxyInfo parsed from configuration map
     * @throws IllegalArgumentException if map is incorrectly structured
     */
    public static DefaultProxyInfo fromMap(PMap conf) {
        var builder = builder();
        var host = conf.getString(KEY_HOST, null);
        var port = conf.getInt(KEY_PORT, 0);
        if (host != null) {
            builder.address(host, port);
        } else {
            builder.address(port);
        }
        builder.typeMatch(conf.getString(KEY_TYPE_PATTERN, "*"));
        builder.idMatch(conf.getString(KEY_ID_PATTERN, "*"));
        var exec = conf.get(KEY_EXEC);
        if (exec != null) {
            var execMap = PMap.from(exec).orElseThrow(IllegalArgumentException::new);
            var command = execMap.getString(KEY_EXEC_COMMAND, VALUE_EXEC_DEFAULT);
            var options = extractArgsList(execMap.get(KEY_EXEC_OPTIONS));
            var args = extractArgsList(execMap.get(KEY_EXEC_ARGS));
            if (VALUE_EXEC_DEFAULT.equals(command)) {
                builder.execDefault(options, args);
            } else {
                builder.execCommand(command, args);
            }
        }
        return new DefaultProxyInfo(builder);
    }
    
    private static List<String> extractArgsList(Value value) {
        if (value == null) {
            return List.of();
        }
        return PArray.from(value).orElseThrow(IllegalArgumentException::new)
                .stream().map(Value::toString).collect(Collectors.toList());
    }
    
    
    static Builder builder() {
        return new Builder();
    }

    static class Builder {

        private static final Pattern DEFAULT_MATCH = globToRegex("*");

        private InetSocketAddress address;
        private Pattern typeMatch;
        private Pattern idMatch;
        private Exec exec;

        private Builder() {
            address = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
            typeMatch = DEFAULT_MATCH;
            idMatch = DEFAULT_MATCH;
            exec = null;
        }

        public Builder address(int port) {
            this.address = new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
            return this;
        }
        
        public Builder address(String host, int port) {
            this.address = new InetSocketAddress(host, port);
            return this;
        }
        
        public Builder typeMatch(String glob) {
            this.typeMatch = globToRegex(glob);
            return this;
        }

        public Builder idMatch(String glob) {
            this.idMatch = globToRegex(glob);
            return this;
        }

        public Builder execDefault(List<String> javaOptions, List<String> arguments) {
            exec = new Exec(Optional.empty(),
                    List.copyOf(javaOptions), List.copyOf(arguments));
            return this;
        }

        public Builder execCommand(String command, List<String> arguments) {
            exec = new Exec(Optional.of(command),
                    List.of(), List.copyOf(arguments));
            return this;
        }

        public DefaultProxyInfo build() {
            return new DefaultProxyInfo(this);
        }

        private static Pattern globToRegex(String glob) {
            StringBuilder regex = new StringBuilder();
            for (char c : glob.toCharArray()) {
                switch (c) {
                    case '*':
                        regex.append(".*");
                        break;
                    case '?':
                        regex.append('.');
                        break;
                    case '|':
                        regex.append('|');
                        break;
                    case '_':
                        regex.append('_');
                        break;
                    case '-':
                        regex.append("\\-");
                        break;
                    default:
                        if (Character.isJavaIdentifierPart(c)) {
                            regex.append(c);
                        } else {
                            throw new IllegalArgumentException();
                        }
                }
            }
            return Pattern.compile(regex.toString());
        }

    }

    private static class Exec implements ProxyInfo.Exec {

        private final Optional<String> command;
        private final List<String> javaOptions;
        private final List<String> arguments;

        Exec(Optional<String> command,
                List<String> javaOptions,
                List<String> arguments) {
            this.command = command;
            this.javaOptions = javaOptions;
            this.arguments = arguments;
        }

        @Override
        public Optional<String> command() {
            return command;
        }

        @Override
        public List<String> javaOptions() {
            return javaOptions;
        }

        @Override
        public List<String> arguments() {
            return arguments;
        }

    }

}
