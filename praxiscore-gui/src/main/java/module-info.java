module org.praxislive.gui {
    
    requires java.desktop;
    requires java.logging;
    
    requires org.praxislive.core;
    requires org.praxislive.base;
    
    requires com.formdev.flatlaf;
    
    provides org.praxislive.core.services.ComponentFactoryProvider with
            org.praxislive.gui.components.GuiFactoryProvider;
    
    opens org.praxislive.gui.components to
            org.praxislive.base;
    
}
