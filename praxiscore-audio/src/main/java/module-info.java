
module org.praxislive.audio {
    
    requires java.logging;
    
    requires org.praxislive.core;
    requires org.praxislive.base;
    requires org.praxislive.util;
    requires org.praxislive.settings;
    requires org.jaudiolibs.pipes;
    requires org.jaudiolibs.audioops;
    requires org.jaudiolibs.audioservers;
    
    exports org.praxislive.audio;
    
    provides org.praxislive.core.Port.TypeProvider with
            org.praxislive.audio.AudioPort.Provider;
    provides org.praxislive.core.services.ComponentFactoryProvider with
            org.praxislive.audio.impl.components.AudioFactoryProvider;
    
    opens org.praxislive.audio.impl.components to
            org.praxislive.base;
    
    uses org.jaudiolibs.audioservers.AudioServerProvider;
    
}
