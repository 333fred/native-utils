package edu.wpi.first.nativeutils

import org.gradle.model.*
import org.gradle.api.Named

@Managed
interface ExportsConfig extends Named {
    void setX86ExcludeSymbols(List<String> symbols)
    List<String> getX86ExcludeSymbols();

    void setX64ExcludeSymbols(List<String> symbols)
    List<String> getX64ExcludeSymbols();
}