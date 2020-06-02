
module org.praxislive.launcher {
    
    requires java.logging;
    requires java.prefs;
    
    requires org.praxislive.base;
    requires org.praxislive.code;
    requires org.praxislive.core;
    requires org.praxislive.hub;
    requires org.praxislive.hub.net;
    
    requires info.picocli;
    
    exports org.praxislive.launcher;
    
    opens org.praxislive.launcher to info.picocli;
    
    provides org.praxislive.core.Settings.Provider with
            org.praxislive.launcher.SettingsProvider;

}
