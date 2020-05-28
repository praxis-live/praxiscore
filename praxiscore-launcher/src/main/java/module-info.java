
module org.praxislive.launcher {
    
    requires java.logging;
    
    requires org.praxislive.base;
    requires org.praxislive.code;
    requires org.praxislive.core;
    requires org.praxislive.hub;
    requires org.praxislive.hub.net;
    
    requires info.picocli;
    
    exports org.praxislive.launcher;
    
    opens org.praxislive.launcher to info.picocli;

}
