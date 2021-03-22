
module org.praxislive.video {
    
    requires java.desktop;
    requires java.logging;
    
    requires org.praxislive.core;
    requires org.praxislive.base;
    
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
    
}
