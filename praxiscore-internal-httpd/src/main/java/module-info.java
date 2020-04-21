
module org.praxislive.internal.httpd {
    
    requires java.logging;
    
    exports org.praxislive.internal.httpd to
            org.praxislive.hub.net;
    
}
