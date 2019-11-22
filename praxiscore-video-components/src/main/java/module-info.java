
module org.praxislive.video.components {
    requires org.praxislive.core;
    requires org.praxislive.code;
    requires org.praxislive.video;
    requires org.praxislive.video.code;
    
    provides org.praxislive.core.services.ComponentFactoryProvider with
            org.praxislive.video.components.VideoComponents;
    
    opens org.praxislive.video.components;
    opens org.praxislive.video.components.resources;
    
}
