def tools = evaluate(io.projectFile("tools.groovy"))

println "The Meaning of life is ${tools.meaningOfLife}"

println tools.speak("to do good")