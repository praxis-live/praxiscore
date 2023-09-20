module org.praxislive.hub.net {

    requires org.praxislive.hub;
    requires org.praxislive.core;
    requires org.praxislive.base;
    requires org.praxislive.script;

    requires com.amazon.ion;
    requires io.netty.common;
    requires io.netty.buffer;
    requires io.netty.codec;
    requires io.netty.codec.http;
    requires io.netty.handler;
    requires io.netty.transport;

    exports org.praxislive.hub.net;

    provides org.praxislive.script.CommandInstaller with
            org.praxislive.hub.net.internal.HubNetCommands;

}
