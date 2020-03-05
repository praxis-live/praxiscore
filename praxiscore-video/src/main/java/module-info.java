
module org.praxislive.video {
    
    requires java.desktop;
    requires java.logging;
    
    requires org.praxislive.core;
    requires org.praxislive.base;
    requires org.praxislive.settings;
    requires org.praxislive.util;
    
    exports org.praxislive.video;
    exports org.praxislive.video.pipes;
    exports org.praxislive.video.pipes.impl;
    exports org.praxislive.video.render;
    exports org.praxislive.video.render.ops;
    exports org.praxislive.video.render.rgbmath;
    exports org.praxislive.video.render.utils;
    exports org.praxislive.video.utils;
    
    provides org.praxislive.core.Port.TypeProvider with 
            org.praxislive.video.VideoPort.Provider;
    provides org.praxislive.core.services.ComponentFactoryProvider with
            org.praxislive.video.impl.components.VideoFactoryProvider;
    
    opens org.praxislive.video.impl.components to
            org.praxislive.base;
    
    uses org.praxislive.video.PlayerFactory.Provider;
    
}
