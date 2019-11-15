
module org.praxislive.script {
    
    requires java.logging;
    
    requires org.praxislive.core;
    requires org.praxislive.base;
    requires org.praxislive.util;
    
    exports org.praxislive.script;
    exports org.praxislive.script.impl;
    
    uses org.praxislive.script.CommandInstaller;
    
}
