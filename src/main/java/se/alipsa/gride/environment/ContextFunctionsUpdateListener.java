package se.alipsa.gride.environment;

import java.util.TreeSet;

public interface ContextFunctionsUpdateListener {

    void updateContextFunctions(TreeSet<String> contextFunctions, TreeSet<String> contaxtObjects);
}
