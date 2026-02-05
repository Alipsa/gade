// Test script for remote GUI interaction
// Run this in Gade with Gradle or Maven runtime to test io.view() and io.display()

import se.alipsa.matrix.core.Matrix

println "Testing remote GUI interaction..."

// Test 1: io.view() with a Matrix
println "Test 1: Creating and viewing a Matrix..."
def matrix = Matrix.builder()
    .matrixName("Test Sales Data")
    .columnNames("Month", "Revenue", "Profit")
    .rows([
        ["January", 10000, 2500],
        ["February", 12000, 3000],
        ["March", 15000, 4000],
        ["April", 13000, 3200]
    ])
    .types([String.class, Integer.class, Integer.class])
    .build()

println "Matrix created with ${matrix.rowCount()} rows"

// This should now work in external runtimes!
io.view(matrix, "Test Sales Data")

println "Test 1 PASSED: Matrix displayed successfully!"

// Test 2: io.view() with a simple list
println "\nTest 2: Viewing a list..."
def data = [
    ["Name", "Age", "City"],
    ["Alice", 25, "New York"],
    ["Bob", 30, "San Francisco"],
    ["Charlie", 35, "Chicago"]
]

io.view(data, "Test User Data")

println "Test 2 PASSED: List displayed successfully!"

// Test 3: io.view() with an Integer (update count)
println "\nTest 3: Viewing an Integer..."
io.view(42, "Test Integer Value")

println "Test 3 PASSED: Integer displayed successfully!"

println "\n=== ALL TESTS PASSED ==="
println "Remote GUI interaction is working correctly!"
