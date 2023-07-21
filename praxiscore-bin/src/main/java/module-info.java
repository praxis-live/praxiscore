
module org.praxislive.bin {
    requires org.praxislive.code;
    requires org.praxislive.launcher;
    requires org.praxislive.purl;

    exports org.praxislive.bin;

    provides org.praxislive.code.LibraryResolver.SystemInfo with
            org.praxislive.bin.BOMSystemInfo;

}
