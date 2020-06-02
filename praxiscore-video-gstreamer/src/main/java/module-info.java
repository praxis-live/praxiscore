
module org.praxislive.video.gstreamer {
    
    requires java.logging;
    
    requires org.freedesktop.gstreamer;
    requires com.sun.jna;
    requires com.sun.jna.platform;
    
    requires org.praxislive.core;
    requires org.praxislive.code;
    requires org.praxislive.logging;
    requires org.praxislive.video.code;
    requires org.praxislive.video;
    
    exports org.praxislive.video.gstreamer;
    exports org.praxislive.video.gstreamer.configuration;

    opens org.praxislive.video.gstreamer.components;
    opens org.praxislive.video.gstreamer.components.resources;
    
    provides org.praxislive.core.services.ComponentFactoryProvider with 
            org.praxislive.video.gstreamer.components.GStreamerComponents;
    provides org.praxislive.code.CodeConnector.Plugin with
            org.praxislive.video.gstreamer.components.GStreamerCodePlugin;
    
}
