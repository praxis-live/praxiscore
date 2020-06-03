
module org.praxislive.video.pgl.code {
    requires java.desktop;
    
    requires org.praxislive.core;
    requires org.praxislive.code;
    requires org.praxislive.video;
    requires org.praxislive.video.pgl;
    requires org.praxislive.libp5x.core;
    
    exports org.praxislive.video.pgl.code;
    exports org.praxislive.video.pgl.code.userapi;
    
    provides org.praxislive.core.services.ComponentFactoryProvider with
            org.praxislive.video.pgl.code.PGLCustomFactoryProvider;
    
    opens org.praxislive.video.pgl.code;
    opens org.praxislive.video.pgl.code.resources;
}
