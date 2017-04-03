/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.http.netty4;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;

import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.http.netty4.pipelining.HttpPipelinedRequest;
import org.elasticsearch.transport.netty4.Netty4Utils;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ChannelHandler.Sharable
class Netty4HttpRequestHandler extends SimpleChannelInboundHandler<Object> {

    private final Netty4HttpServerTransport serverTransport;
    private final boolean httpPipeliningEnabled;
    private final boolean detailedErrorsEnabled;
    private final ThreadContext threadContext;

    Netty4HttpRequestHandler(Netty4HttpServerTransport serverTransport, boolean detailedErrorsEnabled, ThreadContext threadContext) {
        this.serverTransport = serverTransport;
        this.httpPipeliningEnabled = serverTransport.pipelining;
        this.detailedErrorsEnabled = detailedErrorsEnabled;
        this.threadContext = threadContext;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        final FullHttpRequest request;
        final HttpPipelinedRequest pipelinedRequest;
        if (this.httpPipeliningEnabled && msg instanceof HttpPipelinedRequest) {
            pipelinedRequest = (HttpPipelinedRequest) msg;
            request = (FullHttpRequest) pipelinedRequest.last();
        } else {
            pipelinedRequest = null;
            request = (FullHttpRequest) msg;
        }

        String contentString = request.content().toString(StandardCharsets.UTF_8);
        List<String> allMatches = new ArrayList<String>();
        //Find all stings in request.content() matching "u####" pattern
        Matcher m = Pattern.compile("[\\\\][u][A-Za-z0-9][A-Za-z0-9][A-Za-z0-9][A-Za-z0-9]")
                .matcher(contentString);
        while (m.find()) {
            allMatches.add(m.group());
        }

        //Check that all matches fall under valid UTF 8 to Unicode range.
        for (String utf8 : allMatches) {            
            if(!(utf8.matches("[\\\\][u][Cc][2-9A-Ba-b][8-9A-Ba-b][0-9A-Fa-f]")
                    || utf8.matches("[\\\\][u][Dd][0][8][0-8]"))){
                //Throws exception when invalid UTF 8 text is found.
                request.setDecoderResult(DecoderResult.failure(
                        new UnsupportedEncodingException("Invalid UTF-8 character "+utf8+" passed in request body")));
            }
        }
        
        final FullHttpRequest copy =
                new DefaultFullHttpRequest(
                        request.protocolVersion(),
                        request.method(),
                        request.uri(),
                        Unpooled.copiedBuffer(request.content()),
                        request.headers(),
                        request.trailingHeaders());
        final Netty4HttpRequest httpRequest = new Netty4HttpRequest(serverTransport.xContentRegistry, copy, ctx.channel());
        final Netty4HttpChannel channel =
                new Netty4HttpChannel(serverTransport, httpRequest, pipelinedRequest, detailedErrorsEnabled, threadContext);

        if (request.decoderResult().isSuccess()) {
            serverTransport.dispatchRequest(httpRequest, channel);
        } else {
            assert request.decoderResult().isFailure();
            serverTransport.dispatchBadRequest(httpRequest, channel, request.decoderResult().cause());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        Netty4Utils.maybeDie(cause);
        serverTransport.exceptionCaught(ctx, cause);
    }

}
