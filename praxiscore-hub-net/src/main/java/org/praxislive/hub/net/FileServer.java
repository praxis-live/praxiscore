/*
 * Copyright 2023 Neil C Smith
 * Adapted from a Netty example project.
 *
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.praxislive.hub.net;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.CharsetUtil;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;

import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_MODIFIED;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_0;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 *
 */
class FileServer {

    static record Info(int port) {

    }

    private final Path allowedRoot;
    private final int port;

    private Channel serverChannel;
    private EventLoopGroup eventLoopGroup;

    FileServer(Path allowedRoot) {
        this(allowedRoot, 0);
    }

    FileServer(Path allowedRoot, int port) {
        if (!allowedRoot.isAbsolute() || !Files.isDirectory(allowedRoot)) {
            throw new IllegalArgumentException();
        }
        this.allowedRoot = allowedRoot;
        this.port = port;
    }

    synchronized Info start() throws IOException {
        if (eventLoopGroup != null) {
            throw new IllegalStateException("File server not restartable");
        }
        eventLoopGroup = new NioEventLoopGroup();
        try {
            var bootstrap = new ServerBootstrap();
            bootstrap.group(eventLoopGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ch.pipeline().addLast(new HttpServerCodec(),
                                    new HttpObjectAggregator(65536),
                                    new ChunkedWriteHandler(),
                                    new Handler(allowedRoot)
                            );
                        }
                    });
            serverChannel = bootstrap.bind(port).sync().channel();
            return new Info(((InetSocketAddress) serverChannel.localAddress()).getPort());
        } catch (Exception ex) {
            if (eventLoopGroup != null) {
                eventLoopGroup.shutdownGracefully();
            }
            throw new IOException();
        }
    }

    synchronized void stop() {
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (eventLoopGroup != null) {
            eventLoopGroup.shutdownGracefully();
        }
    }

    private static class Handler extends SimpleChannelInboundHandler<FullHttpRequest> {

        private static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
        private static final String HTTP_DATE_GMT_TIMEZONE = "GMT";
        private static final int HTTP_CACHE_SECONDS = 60;

        private final Path allowedRoot;

        private FullHttpRequest request;

        private Handler(Path allowedRoot) {
            this.allowedRoot = allowedRoot;
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
            this.request = request;
            if (!request.decoderResult().isSuccess()) {
                sendError(ctx, BAD_REQUEST);
                return;
            }

            if (!GET.equals(request.method())) {
                sendError(ctx, METHOD_NOT_ALLOWED);
                return;
            }

            final boolean keepAlive = HttpUtil.isKeepAlive(request);
            final String uri = request.uri();
//            final String path = sanitizeUri(uri);

            final Path path = Path.of(new URI("file:///")
                    .resolve(new URI(uri).getRawPath()))
                    .toRealPath();

            if (path == null || !path.startsWith(allowedRoot)) {
                sendError(ctx, FORBIDDEN);
                return;
            }

            if (Files.isHidden(path) || !Files.exists(path)) {
                sendError(ctx, NOT_FOUND);
                return;
            }

            if (Files.isDirectory(path)) {
                if (uri.endsWith("/")) {
                    sendListing(ctx, path, uri);
                } else {
                    sendRedirect(ctx, uri + '/');
                }
                return;
            }

            if (!Files.isRegularFile(path)) {
                sendError(ctx, FORBIDDEN);
                return;
            }

            // Cache Validation
            String ifModifiedSince = request.headers().get(HttpHeaderNames.IF_MODIFIED_SINCE);
            if (ifModifiedSince != null && !ifModifiedSince.isEmpty()) {
                SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
                Date ifModifiedSinceDate = dateFormatter.parse(ifModifiedSince);

                // Only compare up to the second because the datetime format we send to the client
                // does not have milliseconds
                long ifModifiedSinceDateSeconds = ifModifiedSinceDate.getTime() / 1000;
                long fileLastModifiedSeconds = path.toFile().lastModified() / 1000;
                if (ifModifiedSinceDateSeconds == fileLastModifiedSeconds) {
                    sendNotModified(ctx);
                    return;
                }
            }

            RandomAccessFile raf;
            File file = path.toFile();
            try {
                raf = new RandomAccessFile(file, "r");
            } catch (FileNotFoundException ignore) {
                sendError(ctx, NOT_FOUND);
                return;
            }
            long fileLength = raf.length();

            HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
            HttpUtil.setContentLength(response, fileLength);
            setContentTypeHeader(response, file);
            setDateAndCacheHeaders(response, file);

            if (!keepAlive) {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            } else if (request.protocolVersion().equals(HTTP_1_0)) {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            }

            // Write the initial line and the header.
            ctx.write(response);

            // Write the content.
            ChannelFuture sendFileFuture;
            ChannelFuture lastContentFuture;
            sendFileFuture
                    = ctx.write(new DefaultFileRegion(raf.getChannel(), 0, fileLength), ctx.newProgressivePromise());
            // Write the end marker.
            lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
            // Decide whether to close the connection or not.
            if (!keepAlive) {
                // Close the connection when the whole content is written out.
                lastContentFuture.addListener(ChannelFutureListener.CLOSE);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            if (ctx.channel().isActive()) {
                sendError(ctx, INTERNAL_SERVER_ERROR);
            }
        }

//        private static final Pattern INSECURE_URI = Pattern.compile(".*[<>&\"].*");
//
//        private static String sanitizeUri(String uri) {
//            // Decode the path.
//            try {
//                uri = URLDecoder.decode(uri, "UTF-8");
//            } catch (UnsupportedEncodingException e) {
//                throw new Error(e);
//            }
//
//            if (uri.isEmpty() || uri.charAt(0) != '/') {
//                return null;
//            }
//
//            // Convert file separators.
//            uri = uri.replace('/', File.separatorChar);
//
//            // Simplistic dumb security check.
//            // You will have to do something serious in the production environment.
//            if (uri.contains(File.separator + '.')
//                    || uri.contains('.' + File.separator)
//                    || uri.charAt(0) == '.' || uri.charAt(uri.length() - 1) == '.'
//                    || INSECURE_URI.matcher(uri).matches()) {
//                return null;
//            }
//
//            // Convert to absolute path.
//            return SystemPropertyUtil.get("user.dir") + File.separator + uri;
//        }
        private static final Pattern ALLOWED_FILE_NAME = Pattern.compile("[^-\\._]?[^<>&\\\"]*");

        private void sendListing(ChannelHandlerContext ctx, Path dir, String dirPath) {
            StringBuilder buf = new StringBuilder()
                    .append("<!DOCTYPE html>\r\n")
                    .append("<html><head><meta charset='utf-8' /><title>")
                    .append("Listing of: ")
                    .append(dirPath)
                    .append("</title></head><body>\r\n")
                    .append("<h3>Listing of: ")
                    .append(dirPath)
                    .append("</h3>\r\n")
                    .append("<ul>")
                    .append("<li><a href=\"../\">..</a></li>\r\n");

            File[] files = dir.toFile().listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isHidden() || !f.canRead()) {
                        continue;
                    }

                    String name = f.getName();
                    if (!ALLOWED_FILE_NAME.matcher(name).matches()) {
                        continue;
                    }

                    buf.append("<li><a href=\"")
                            .append(name)
                            .append("\">")
                            .append(name)
                            .append("</a></li>\r\n");
                }
            }

            buf.append("</ul></body></html>\r\n");

            ByteBuf buffer = ctx.alloc().buffer(buf.length());
            buffer.writeCharSequence(buf.toString(), CharsetUtil.UTF_8);

            FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK, buffer);
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8");

            sendAndCleanupConnection(ctx, response);
        }

        private void sendRedirect(ChannelHandlerContext ctx, String newUri) {
            FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, FOUND, Unpooled.EMPTY_BUFFER);
            response.headers().set(HttpHeaderNames.LOCATION, newUri);

            sendAndCleanupConnection(ctx, response);
        }

        private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HTTP_1_1, status, Unpooled.copiedBuffer("Failure: " + status + "\r\n", CharsetUtil.UTF_8));
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");

            sendAndCleanupConnection(ctx, response);
        }

        /**
         * When file timestamp is the same as what the browser is sending up,
         * send a "304 Not Modified"
         *
         * @param ctx Context
         */
        private void sendNotModified(ChannelHandlerContext ctx) {
            FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, NOT_MODIFIED, Unpooled.EMPTY_BUFFER);
            setDateHeader(response);

            sendAndCleanupConnection(ctx, response);
        }

        /**
         * If Keep-Alive is disabled, attaches "Connection: close" header to the
         * response and closes the connection after the response being sent.
         */
        private void sendAndCleanupConnection(ChannelHandlerContext ctx, FullHttpResponse response) {
            final FullHttpRequest request = this.request;
            final boolean keepAlive = HttpUtil.isKeepAlive(request);
            HttpUtil.setContentLength(response, response.content().readableBytes());
            if (!keepAlive) {
                // We're going to close the connection as soon as the response is sent,
                // so we should also make it clear for the client.
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            } else if (request.protocolVersion().equals(HTTP_1_0)) {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            }

            ChannelFuture flushPromise = ctx.writeAndFlush(response);

            if (!keepAlive) {
                // Close the connection as soon as the response is sent.
                flushPromise.addListener(ChannelFutureListener.CLOSE);
            }
        }

        /**
         * Sets the Date header for the HTTP response
         *
         * @param response HTTP response
         */
        private static void setDateHeader(FullHttpResponse response) {
            SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
            dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

            Calendar time = new GregorianCalendar();
            response.headers().set(HttpHeaderNames.DATE, dateFormatter.format(time.getTime()));
        }

        /**
         * Sets the Date and Cache headers for the HTTP Response
         *
         * @param response HTTP response
         * @param fileToCache file to extract content type
         */
        private static void setDateAndCacheHeaders(HttpResponse response, File fileToCache) {
            SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
            dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

            // Date header
            Calendar time = new GregorianCalendar();
            response.headers().set(HttpHeaderNames.DATE, dateFormatter.format(time.getTime()));

            // Add cache headers
            time.add(Calendar.SECOND, HTTP_CACHE_SECONDS);
            response.headers().set(HttpHeaderNames.EXPIRES, dateFormatter.format(time.getTime()));
            response.headers().set(HttpHeaderNames.CACHE_CONTROL, "private, max-age=" + HTTP_CACHE_SECONDS);
            response.headers().set(
                    HttpHeaderNames.LAST_MODIFIED, dateFormatter.format(new Date(fileToCache.lastModified())));
        }

        /**
         * Sets the content type header for the HTTP Response
         *
         * @param response HTTP response
         * @param file file to extract content type
         */
        private static void setContentTypeHeader(HttpResponse response, File file) {
//        MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
//        response.headers().set(HttpHeaderNames.CONTENT_TYPE, mimeTypesMap.getContentType(file.getPath()));
        }
    }

}
