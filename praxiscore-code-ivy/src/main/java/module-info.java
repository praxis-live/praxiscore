
module org.praxislive.code.ivy {
    
    requires org.praxislive.core;
    requires org.praxislive.code;
    requires org.apache.ivy;

    provides org.praxislive.code.LibraryResolver.Provider with
            org.praxislive.code.services.ivy.IvyResolver.Provider;
    
}
