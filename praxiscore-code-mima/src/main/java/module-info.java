
module org.praxislive.code.mima {
    
    requires org.praxislive.core;
    requires org.praxislive.code;
    requires org.praxislive.purl;
    requires org.slf4j;
    requires org.apache.commons.logging;
    requires eu.maveniverse.maven.mima.context;
    requires eu.maveniverse.maven.mima.runtime.standalonestatic;

    provides org.praxislive.code.LibraryResolver.Provider with
            org.praxislive.code.services.mima.MimaResolver.Provider;
    
}
