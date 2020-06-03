module org.praxislive.hub.net {
    
    requires java.logging;
    
    requires org.praxislive.hub;
    requires org.praxislive.core;
    requires org.praxislive.base;
    requires org.praxislive.script;
    
    requires org.praxislive.internal.httpd;
    requires org.praxislive.internal.osc;
    
    exports org.praxislive.hub.net;
    
    provides org.praxislive.script.CommandInstaller with
            org.praxislive.hub.net.internal.HubNetCommands;
    
}
