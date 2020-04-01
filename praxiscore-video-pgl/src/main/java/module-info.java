
module org.praxislive.video.pgl {
    
    requires java.desktop;
    requires java.logging;
    
    requires org.praxislive.core;
    requires org.praxislive.video;
    requires org.praxislive.libp5x.core;
    requires org.praxislive.libp5x.lwjgl;
    requires org.lwjgl;
    requires org.lwjgl.egl;
    requires org.lwjgl.glfw;
    requires org.lwjgl.opengl;
    requires org.lwjgl.stb;
    
    exports org.praxislive.video.pgl;
    
    provides org.praxislive.video.PlayerFactory.Provider with
            org.praxislive.video.pgl.PGLPlayerFactory.Default;
    
    opens org.praxislive.video.pgl to org.praxislive.video;
    
}
