
module org.praxislive.video.components {
    
    requires java.desktop;
    
    requires org.praxislive.core;
    requires org.praxislive.base;
    requires org.praxislive.code;
    requires org.praxislive.video;
    requires org.praxislive.video.code;
    
    provides org.praxislive.core.services.ComponentFactoryProvider with
            org.praxislive.video.components.VideoComponents,
            org.praxislive.video.impl.components.VideoFactoryProvider;
    
    opens org.praxislive.video.components;
    opens org.praxislive.video.components.resources;
    
    opens org.praxislive.video.impl.components to
            org.praxislive.base;
    
    uses org.praxislive.video.PlayerFactory.Provider;
    
}
