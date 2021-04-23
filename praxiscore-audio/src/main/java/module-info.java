
module org.praxislive.audio {
    
    requires org.praxislive.core;
    requires org.jaudiolibs.pipes;
    
    exports org.praxislive.audio;
    
    provides org.praxislive.core.Port.TypeProvider with
            org.praxislive.audio.AudioPort.Provider;
    
}
