// Simplistic groovy project

import se.alipsa.groovy.matrix.Matrix
import java.time.LocalDate

def borrower = args.length > 0 ? args[0] : "Per"

table = Matrix.builder()
    .data([
        id: [1, 2],
        name: ["Dao De Jing", "The Analects"],
        checked_out: [LocalDate.now(), null],
        borrower: [borrower, null]
    ])
    .build()
// Note: by not using "def" or type, variables can be accessed from tests as if we explicitly did
//binding.setVariable("table", table)

// Print the result to console
println table.content()
