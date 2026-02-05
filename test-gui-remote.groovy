// Simple test script to debug remote GUI interaction
println "Script starting..."
println "About to call io.scriptDir()..."

try {
    def dir = io.scriptDir()
    println "scriptDir() returned: ${dir}"
    println "scriptDir() class: ${dir?.class?.name}"
} catch (Exception e) {
    println "ERROR calling scriptDir(): ${e.message}"
    e.printStackTrace()
}

println "Script done."
