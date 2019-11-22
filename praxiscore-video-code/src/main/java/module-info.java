
module org.praxislive.video.code {
    
    requires java.desktop;
    
    requires org.praxislive.core;
    requires org.praxislive.code;
    requires org.praxislive.logging;
    requires org.praxislive.video;
    
    exports org.praxislive.video.code;
    exports org.praxislive.video.code.userapi;
    
    opens org.praxislive.video.code.resources;
    
}
