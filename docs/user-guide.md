# Gade User Guide

**Version:** 1.0.0
**Last Updated:** February 3, 2026

This guide provides detailed instructions for using Gade effectively for data analysis, visualization, and Groovy development.

## Table of Contents

1. [Getting Started](#getting-started)
2. [Runtime Selection](#runtime-selection)
3. [Code Completion](#code-completion)
4. [Database Connections](#database-connections)
5. [Git Integration](#git-integration)
6. [Charts and Visualizations](#charts-and-visualizations)
7. [Keyboard Shortcuts](#keyboard-shortcuts)
8. [Advanced Topics](#advanced-topics)

---

## Getting Started

### First Steps

After installing Gade, launch the application and you'll see:

- **Menu Bar:** File, Edit, Code, View, Tools, Git, Help
- **Toolbar:** Quick access to common actions (New, Open, Save, Run)
- **Editor Area:** Tabbed interface for editing multiple files
- **Console:** Output from script execution
- **Environment Tab:** Variables and objects created during execution
- **Files Tab:** Project file browser
- **Connections Tab:** Database connection management

### Creating Your First Project

1. **Create a workspace directory:**
   ```bash
   mkdir ~/my-data-project
   cd ~/my-data-project
   ```

2. **Launch Gade:**
   ```bash
   gade.sh  # or gade.cmd on Windows
   ```

3. **Create a new script:**
   - Click **File → New Script** (Ctrl+N)
   - Save as `analysis.groovy`

4. **Write some code:**
   ```groovy
   println "Hello from Gade!"
   def numbers = [1, 2, 3, 4, 5]
   println "Sum: ${numbers.sum()}"
   println "Average: ${numbers.sum() / numbers.size()}"
   ```

5. **Execute the script:**
   - Press **F5** or click the **Run** button
   - View output in the Console tab

---

## Runtime Selection

Gade supports four runtime types. All runtimes execute scripts in a separate subprocess with a clean classpath, ensuring full isolation from the IDE.

### Runtime Overview

| Feature | Gade | Gradle | Maven | Custom |
|---------|------|--------|-------|--------|
| **Purpose** | Scripting, ad-hoc analysis | Gradle-based projects | Maven-based projects | Full control over JVM/Groovy |
| **Classpath source** | JDK + Groovy | `build.gradle` dependencies | `pom.xml` dependencies | User-specified jars |
| **Build file required** | No | `build.gradle` | `pom.xml` | No |
| **`@Grab` support** | Yes | Yes | Yes | Yes |
| **Custom JVM** | No (uses bundled) | Configurable | Configurable | Configurable |

### Gade Runtime

This is a "bare" runtime with the JDK and Groovy on the classpath. The primary use is for scripting — creating an analysis, doing simple tasks, or exploring data without needing a build system.

**Best for:**
- Quick data exploration and ad-hoc analysis
- Simple scripts and prototyping
- Learning Groovy and testing code snippets
- Scripts that use `@Grab` to pull in dependencies on the fly

**How to use:**
1. Select **Gade** from the **Runtimes** menu
2. Write and run your script

**Example:**
```groovy
// Built-in classes are available directly
import se.alipsa.groovy.matrix.*

def data = Matrix.builder()
  .data([[1, 10], [2, 20], [3, 30]])
  .columnNames('x', 'y')
  .build()

println data.summary()
```

**Using `@Grab` to add dependencies:**
```groovy
@Grab('org.apache.commons:commons-math3:3.6.1')
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics

def stats = new DescriptiveStatistics()
[1, 2, 3, 4, 5].each { stats.addValue(it) }
println "Mean: ${stats.mean}"
```

---

### Gradle Runtime

This is meant to be used for projects where you use Gradle as the build system. The runtime classpath is created based on the dependencies defined in the Gradle build script. Only available when a `build.gradle` file exists in the project root.

**Best for:**
- Projects requiring external libraries (Apache Commons, Guava, etc.)
- Complex data science workflows with specialized libraries
- Building reusable Groovy libraries or applications
- Projects you want to share with others (via build.gradle)

**Setup:**

1. **Create build.gradle in your project:**
   ```groovy
   plugins {
       id 'groovy'
   }

   repositories {
       mavenCentral()
   }

   dependencies {
       implementation 'org.apache.commons:commons-math3:3.6.1'
       implementation 'com.google.guava:guava:31.1-jre'
       implementation 'org.apache.commons:commons-csv:1.9.0'
   }
   ```

2. **Select Gradle from the Runtimes menu**
   - Gade automatically detects build.gradle

3. **Run your script:**
   ```groovy
   import org.apache.commons.math3.stat.descriptive.*
   import com.google.common.collect.ImmutableList

   def stats = new DescriptiveStatistics()
   [1, 2, 3, 4, 5].each { stats.addValue(it) }

   println "Mean: ${stats.mean}"
   println "Std Dev: ${stats.standardDeviation}"
   ```

**First-run behavior:**
- Gradle downloads dependencies (may take time)
- Subsequent runs use cached dependencies (fast)

---

### Maven Runtime

This is meant to be used for projects where you use Maven as the build system. The runtime classpath is created based on the dependencies defined in the Maven POM file. Only available when a `pom.xml` file exists in the project root.

**Best for:**
- Enterprise projects using Maven
- Projects with complex POM inheritance
- Integration with existing Maven-based workflows
- Organizations standardized on Maven

**Setup:**

1. **Create pom.xml in your project:**
   ```xml
   <?xml version="1.0" encoding="UTF-8"?>
   <project xmlns="http://maven.apache.org/POM/4.0.0"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
            http://maven.apache.org/xsd/maven-4.0.0.xsd">
       <modelVersion>4.0.0</modelVersion>

       <groupId>com.example</groupId>
       <artifactId>my-analysis</artifactId>
       <version>1.0-SNAPSHOT</version>

       <dependencies>
           <dependency>
               <groupId>org.apache.groovy</groupId>
               <artifactId>groovy-all</artifactId>
               <version>4.0.15</version>
               <type>pom</type>
           </dependency>
           <dependency>
               <groupId>org.apache.commons</groupId>
               <artifactId>commons-math3</artifactId>
               <version>3.6.1</version>
           </dependency>
       </dependencies>
   </project>
   ```

2. **Select Maven from the Runtimes menu**

3. **Run your script:**
   - All dependencies from pom.xml are available

---

### Custom Runtime

This is a runtime where you specify the JVM and Groovy installation that you want to use. You also have the possibility to add additional jars to the classpath and declare dependencies using Gradle short notation (`groupId:artifactId:version`).

**Best for:**
- Using a specific JDK version different from the one bundled with Gade
- Using a specific Groovy version
- Environments where you need precise control over the classpath
- Testing scripts against different JVM/Groovy combinations

**Setup:**

1. Go to **Runtimes → Add custom runtime**
2. Configure the runtime:
   - **Name:** A descriptive name (e.g., "Java 17 + Groovy 4.0")
   - **JVM Home:** Path to the JDK installation (optional — defaults to the bundled JDK)
   - **Groovy Home:** Path to a Groovy installation (optional — defaults to bundled Groovy)
   - **Additional JARs:** Extra jar files to add to the classpath
   - **Dependencies:** Maven coordinates in Gradle short notation (e.g., `org.apache.commons:commons-math3:3.6.1`)
3. Select your custom runtime from the **Runtimes** menu

**Editing or deleting custom runtimes:**
- Go to **Runtimes → Edit custom runtimes** to modify, add, or remove custom runtimes

---

### Using `@Grab` Annotations

`@Grab` annotations work with all runtime types and can be used to add dependencies to the classpath at script compilation time. Since all runtimes execute in an isolated subprocess, `@Grab` downloads are fully separated from the IDE classpath.

```groovy
@Grab('tech.tablesaw:tablesaw-core:0.44.1')
@Grab('tech.tablesaw:tablesaw-html:0.44.1')
import tech.tablesaw.api.Table

def table = Table.read().url("https://example.com/data.csv")
println table.structure()
```

`@GrabConfig(systemClassLoader=true)` also works safely since it only affects the subprocess JVM:

```groovy
@GrabConfig(systemClassLoader=true)
@Grab('com.h2database:h2:2.2.224')
import java.sql.DriverManager

def conn = DriverManager.getConnection("jdbc:h2:mem:test")
println "Connected: ${conn.catalog}"
conn.close()
```

**Caution with Gradle and Maven runtimes:** Using `@Grab` in projects that already have a build system (Gradle or Maven) managing dependencies can easily become confusing and hard to maintain. The `@Grab` dependencies are invisible to the build system, which means other developers won't know about them, builds may not be reproducible, and version conflicts can arise. Use `@Grab` with utmost care in Gradle and Maven projects — prefer declaring dependencies in the build file instead.

---

### Switching Between Runtimes

You can switch runtimes at any time:

1. Select the desired runtime from the **Runtimes** menu
2. The active runtime is marked with a checkmark
3. Scripts will execute in the new runtime on next run

The runtime selection is stored per project directory, so different projects can use different runtimes.

**Note:** If a selected runtime becomes unavailable (e.g., a `build.gradle` file is deleted while the Gradle runtime is active), Gade will prompt you to select an alternative runtime.

---

## Code Completion

Gade provides intelligent code completion to speed up development.

### Basic Usage

**Trigger completion:**
- Press **Ctrl+Space** at any cursor position
- Or start typing and completion appears automatically

**Navigate suggestions:**
- Use **↑/↓ arrow keys** to navigate
- Press **Enter** to accept suggestion
- Press **Esc** to dismiss

### Completion Types

#### 1. Member Access Completion

Type a dot (`.`) after an object to see available methods and properties:

```groovy
def text = "hello world"
text.  // Shows: toUpperCase(), toLowerCase(), size(), etc.
```

```groovy
import java.time.LocalDate

def today = LocalDate.now()
today.  // Shows: getYear(), getMonth(), getDayOfMonth(), plusDays(), etc.
```

#### 2. Import Completion

Start typing a class name and get import suggestions:

```groovy
LocalDa  // Suggests: LocalDate, LocalDateTime
// Accept → automatically adds: import java.time.LocalDate
```

#### 3. Keyword Completion

Type Groovy/Java keywords:

```groovy
cla  // Suggests: class
def  // Suggests: def, default
pri  // Suggests: private, println
```

#### 4. SQL Completion

In SQL scripts, get keyword and table name suggestions:

```sql
SEL  -- Suggests: SELECT
FROM us  -- Suggests: users, user_roles (from connected database)
```

#### 5. Variable Completion

Variables in scope are suggested:

```groovy
def userName = "Alice"
def userAge = 30

user  // Suggests: userName, userAge
```

### Context-Aware Completion

Gade analyzes your code context:

**Inside strings:** No completion (literal text)
```groovy
def x = "hel|"  // No suggestions inside string
```

**After 'new' keyword:** Shows constructors
```groovy
new ArrayLi  // Suggests: ArrayList, with constructor signatures
```

**After '@' symbol:** Shows annotations
```groovy
@Over  // Suggests: @Override
```

### Completion Performance

Code completion is optimized for speed:

- **Target:** < 100ms response time
- **Caching:** Classpaths are cached for faster subsequent lookups
- **Indexing:** Background indexing of project classes

**If completion is slow:**
1. Check runtime selection (Gade Runtime is fastest)
2. Ensure Gradle/Maven dependencies are resolved
3. Increase heap memory: `JAVA_OPTS="-Xmx8G"`

### Advanced Completion Features

#### Groovy Dynamic Features

Gade handles Groovy's dynamic nature:

```groovy
def map = [name: 'Alice', age: 30]
map.  // Shows: get(), put(), name, age (map methods + keys)
```

#### Closure Parameters

Completion inside closures:

```groovy
[1, 2, 3].each { num ->
    num.  // Shows Integer methods
}
```

#### Builder Patterns

Completion for builder-style APIs:

```groovy
import se.alipsa.groovy.matrix.*

Matrix.builder()
  .data()  // Shows: data(), columns(), columnNames(), build()
```

---

## Database Connections

Gade provides comprehensive support for working with databases via JDBC.

### Adding a Database Connection

#### Step 1: Add JDBC Driver

**Option A: Via UI (Recommended)**
1. Click **Connections** tab
2. Right-click → **Add JDBC Driver**
3. Browse to your driver JAR file (e.g., `postgresql-42.5.0.jar`)
4. Click **Open**

**Option B: Via Gradle**
```groovy
// Add to build.gradle
dependencies {
    implementation 'org.postgresql:postgresql:42.5.0'
    implementation 'mysql:mysql-connector-java:8.0.30'
    implementation 'com.h2database:h2:2.1.214'
}
```

#### Step 2: Create Connection

1. Right-click **Connections** → **Add Connection**
2. Fill in connection details:

**H2 (Embedded Database):**
```
Name: LocalH2
Driver: org.h2.Driver
URL: jdbc:h2:file:./data/mydb
Username: sa
Password: (leave empty)
```

**PostgreSQL:**
```
Name: PostgresDB
Driver: org.postgresql.Driver
URL: jdbc:postgresql://localhost:5432/mydb
Username: postgres
Password: your_password
```

**MySQL:**
```
Name: MySQLDB
Driver: com.mysql.cj.jdbc.Driver
URL: jdbc:mysql://localhost:3306/mydb?useSSL=false
Username: root
Password: your_password
```

**SQL Server:**
```
Name: SQLServerDB
Driver: com.microsoft.sqlserver.jdbc.SQLServerDriver
URL: jdbc:sqlserver://localhost:1433;databaseName=mydb
Username: sa
Password: your_password
```

3. Click **Test Connection** to verify
4. Click **Save**

### Using Connections in Scripts

#### Execute SQL Queries

```groovy
import groovy.sql.Sql

// Get connection by name
def conn = connections.getConnection('PostgresDB')
def sql = new Sql(conn)

// Execute query
def results = sql.rows('SELECT * FROM users WHERE age > ?', [25])

results.each { row ->
    println "${row.name} is ${row.age} years old"
}

// Always close when done
sql.close()
```

#### Insert Data

```groovy
import groovy.sql.Sql

def conn = connections.getConnection('PostgresDB')
def sql = new Sql(conn)

sql.execute('''
    INSERT INTO users (name, age, email)
    VALUES (?, ?, ?)
''', ['Alice', 30, 'alice@example.com'])

println "Inserted ${sql.updateCount} row(s)"
sql.close()
```

#### Batch Operations

```groovy
import groovy.sql.Sql

def conn = connections.getConnection('PostgresDB')
def sql = new Sql(conn)

def users = [
    ['Bob', 25, 'bob@example.com'],
    ['Charlie', 35, 'charlie@example.com'],
    ['Diana', 28, 'diana@example.com']
]

sql.withBatch(100) { stmt ->
    users.each { user ->
        stmt.addBatch(
            'INSERT INTO users (name, age, email) VALUES (?, ?, ?)',
            user
        )
    }
}

println "Batch insert complete"
sql.close()
```

#### Working with Result Sets

```groovy
import groovy.sql.Sql
import se.alipsa.groovy.matrix.*

def conn = connections.getConnection('PostgresDB')
def sql = new Sql(conn)

// Query to Matrix (like R data.frame)
def matrix = Matrix.create(sql, 'SELECT * FROM sales WHERE year = 2023')

println matrix.summary()
println "Total sales: ${matrix.sum('amount')}"
println "Average: ${matrix.mean('amount')}"

sql.close()
```

### Connection Management

#### View Connection Details

1. Right-click a connection → **View Connection**
2. See: Driver, URL, username, connection status

#### Test Connection

1. Right-click a connection → **Test Connection**
2. Verifies connectivity and credentials

#### Edit Connection

1. Right-click a connection → **Edit**
2. Modify URL, credentials, or driver
3. Click **Save**

#### Delete Connection

1. Right-click a connection → **Delete**
2. Confirm deletion

### SQL Script Execution

#### Create SQL Script

1. **File → New Script**
2. Select **SQL** as file type
3. Save as `queries.sql`

#### Execute SQL

```sql
-- Select database (if connection supports it)
USE mydb;

-- Create table
CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100),
    age INTEGER,
    email VARCHAR(255)
);

-- Insert data
INSERT INTO users (name, age, email) VALUES
    ('Alice', 30, 'alice@example.com'),
    ('Bob', 25, 'bob@example.com');

-- Query
SELECT * FROM users WHERE age > 25;
```

**To execute:**
- Select specific SQL statement
- Press **F5** or click **Run**
- Results appear in the **Viewer** tab

### Advanced Database Features

#### Transaction Management

```groovy
import groovy.sql.Sql

def conn = connections.getConnection('PostgresDB')
def sql = new Sql(conn)

sql.withTransaction {
    sql.execute('UPDATE accounts SET balance = balance - 100 WHERE id = 1')
    sql.execute('UPDATE accounts SET balance = balance + 100 WHERE id = 2')
    // Automatically commits if no exceptions
    // Automatically rolls back if exception occurs
}

sql.close()
```

#### Connection Pooling

For better performance with multiple queries:

```groovy
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import groovy.sql.Sql

// Create connection pool (do once)
def config = new HikariConfig()
config.jdbcUrl = 'jdbc:postgresql://localhost:5432/mydb'
config.username = 'postgres'
config.password = 'password'
config.maximumPoolSize = 10

def dataSource = new HikariDataSource(config)

// Use pooled connections
def sql = new Sql(dataSource)
def results = sql.rows('SELECT * FROM users')
sql.close()  // Returns connection to pool

// Shutdown pool when done
dataSource.close()
```

#### Metadata Introspection

```groovy
import groovy.sql.Sql

def conn = connections.getConnection('PostgresDB')
def sql = new Sql(conn)

// List all tables
def tables = []
conn.metaData.getTables(null, null, '%', ['TABLE'] as String[]).each {
    tables << it.TABLE_NAME
}
println "Tables: ${tables}"

// Get column info for a table
def columns = []
conn.metaData.getColumns(null, null, 'users', '%').each {
    columns << [
        name: it.COLUMN_NAME,
        type: it.TYPE_NAME,
        size: it.COLUMN_SIZE
    ]
}
println "Columns: ${columns}"

sql.close()
```

---

## Git Integration

Gade includes built-in Git support for version control.

### Initial Setup

#### Configure Git Identity

1. Go to **Git → Settings**
2. Enter your name and email:
   ```
   Name: John Doe
   Email: john@example.com
   ```
3. Click **Save**

This sets `git config user.name` and `git config user.email` for commits.

### Cloning a Repository

1. **Git → Clone Repository**
2. Enter repository URL:
   ```
   HTTPS: https://github.com/username/repo.git
   SSH: git@github.com:username/repo.git
   ```
3. Choose local directory
4. Click **Clone**

**Authentication:**
- **HTTPS:** Use personal access token (not password)
- **SSH:** Ensure SSH key is configured (see [SSH Setup](#ssh-key-setup))

### Creating a New Repository

#### Option 1: Initialize in Existing Project

1. Open project directory in Gade
2. **Git → Initialize Repository**
3. Creates `.git` directory in current folder

#### Option 2: Create on GitHub/GitLab, Then Clone

1. Create repository on GitHub/GitLab
2. Use **Git → Clone Repository** in Gade

### Basic Workflow

#### 1. Check Status

**Git → Status** (or Ctrl+G, S)

Shows:
- Modified files (red)
- Staged files (green)
- Untracked files (gray)

#### 2. Stage Changes

**Option A: Via Git Menu**
1. **Git → Stage Changes**
2. Select files to stage
3. Click **Stage**

**Option B: Via Console**
```bash
# In Gade console
git add analysis.groovy
git add data/*.csv
```

#### 3. Commit Changes

**Git → Commit** (or Ctrl+G, C)

1. Enter commit message:
   ```
   Add data cleaning script

   - Remove outliers from dataset
   - Normalize column names
   - Handle missing values
   ```
2. Click **Commit**

**Commit message best practices:**
- First line: Short summary (< 50 chars)
- Blank line
- Detailed description (if needed)

#### 4. View History

**Git → Log** (or Ctrl+G, L)

Shows:
- Commit hashes
- Authors
- Dates
- Commit messages

**Navigate:**
- Click commit to see details
- Right-click → **Show Diff** to see changes

#### 5. Push to Remote

**Git → Push** (or Ctrl+G, P)

1. Select remote (usually `origin`)
2. Select branch (usually `main` or `master`)
3. Click **Push**

**First-time push:**
```bash
# Set upstream branch
git push -u origin main
```

#### 6. Pull from Remote

**Git → Pull** (or Ctrl+G, U)

1. Select remote and branch
2. Click **Pull**
3. Merges remote changes into local branch

### Branching and Merging

#### Create a New Branch

**Git → Branches → New Branch**

1. Enter branch name: `feature/data-visualization`
2. Click **Create**
3. Automatically switches to new branch

```bash
# Equivalent command
git checkout -b feature/data-visualization
```

#### Switch Branches

**Git → Branches → Checkout**

1. Select branch from list
2. Click **Checkout**

#### Merge Branches

**Git → Merge**

1. Ensure you're on target branch (e.g., `main`)
2. Select branch to merge (e.g., `feature/data-visualization`)
3. Click **Merge**

**If conflicts occur:**
1. Gade highlights conflicting files
2. Open files and resolve conflicts manually
3. Look for conflict markers:
   ```
   <<<<<<< HEAD
   Your changes
   =======
   Their changes
   >>>>>>> feature-branch
   ```
4. Edit to keep desired changes
5. Remove conflict markers
6. Stage resolved files
7. Commit the merge

### Advanced Git Operations

#### View Diff

**See changes before committing:**

1. Right-click modified file → **Show Diff**
2. Or **Git → Diff**
3. View side-by-side comparison

#### Discard Changes

**Revert file to last commit:**

1. Right-click file → **Discard Changes**
2. Confirm (this is destructive!)

```bash
# Equivalent command
git checkout -- analysis.groovy
```

#### Stash Changes

**Save work-in-progress without committing:**

1. **Git → Stash → Save Stash**
2. Enter stash description
3. Working directory is now clean

**Apply stash later:**
1. **Git → Stash → Apply Stash**
2. Select stash from list
3. Changes are reapplied

```bash
# Equivalent commands
git stash save "WIP: data cleaning"
git stash list
git stash apply stash@{0}
```

#### Tagging Releases

**Create a version tag:**

1. **Git → Tag → Create Tag**
2. Enter tag name: `v1.0.0`
3. Enter tag message: `First stable release`
4. Click **Create**

**Push tags to remote:**
```bash
git push --tags
```

### SSH Key Setup

For SSH-based Git operations:

1. **Generate SSH key (if you don't have one):**
   ```bash
   ssh-keygen -t ed25519 -C "your_email@example.com"
   # Press Enter to accept default location (~/.ssh/id_ed25519)
   # Enter passphrase (optional)
   ```

2. **Add to SSH agent:**
   ```bash
   eval "$(ssh-agent -s)"
   ssh-add ~/.ssh/id_ed25519
   ```

3. **Copy public key:**
   ```bash
   cat ~/.ssh/id_ed25519.pub
   # Copy the output
   ```

4. **Add to GitHub/GitLab:**
   - GitHub: Settings → SSH and GPG keys → New SSH key
   - GitLab: Preferences → SSH Keys → Add key
   - Paste public key

5. **Test connection:**
   ```bash
   ssh -T git@github.com
   # Should see: "Hi username! You've successfully authenticated..."
   ```

---

## Charts and Visualizations

Gade includes powerful visualization capabilities using the Matrix library and FreeCharts.

### Setting Up for Visualization

**Import required libraries:**
```groovy
import se.alipsa.groovy.matrix.*
import se.alipsa.groovy.charts.*
import tech.tablesaw.plotly.Plot
import tech.tablesaw.plotly.api.*
```

### Creating Data for Charts

#### Using Matrix (Recommended)

```groovy
import se.alipsa.groovy.matrix.*

// From arrays
def data = Matrix.builder()
  .data([
    [1, 10, 'A'],
    [2, 20, 'B'],
    [3, 15, 'C'],
    [4, 25, 'D'],
    [5, 30, 'E']
  ])
  .columnNames('x', 'y', 'category')
  .build()

// From CSV
def csvData = Matrix.create(new File('data.csv'))

// From database
import groovy.sql.Sql
def conn = connections.getConnection('MyDB')
def sql = new Sql(conn)
def dbData = Matrix.create(sql, 'SELECT * FROM sales')
sql.close()
```

### Line Charts

```groovy
import se.alipsa.groovy.matrix.*
import se.alipsa.groovy.charts.*

// Create sample data
def data = Matrix.builder()
  .data([
    [1, 10], [2, 15], [3, 13], [4, 20], [5, 25],
    [6, 22], [7, 30], [8, 28], [9, 35], [10, 40]
  ])
  .columnNames('month', 'sales')
  .build()

// Create line chart
def chart = LineChart.create()
  .title('Monthly Sales Trend')
  .xAxisLabel('Month')
  .yAxisLabel('Sales ($1000s)')
  .data(data, 'month', 'sales')
  .build()

// Display chart
chart.show()

// Or save to file
chart.save(new File('sales_trend.png'), 800, 600)
```

**Multiple series:**
```groovy
def data = Matrix.builder()
  .data([
    [1, 10, 12], [2, 15, 18], [3, 13, 16],
    [4, 20, 22], [5, 25, 28]
  ])
  .columnNames('month', 'product_a', 'product_b')
  .build()

def chart = LineChart.create()
  .title('Product Comparison')
  .xAxisLabel('Month')
  .yAxisLabel('Sales')
  .series('Product A', data, 'month', 'product_a')
  .series('Product B', data, 'month', 'product_b')
  .build()

chart.show()
```

### Bar Charts

```groovy
import se.alipsa.groovy.matrix.*
import se.alipsa.groovy.charts.*

def data = Matrix.builder()
  .data([
    ['Q1', 100],
    ['Q2', 120],
    ['Q3', 110],
    ['Q4', 140]
  ])
  .columnNames('quarter', 'revenue')
  .build()

def chart = BarChart.create()
  .title('Quarterly Revenue')
  .xAxisLabel('Quarter')
  .yAxisLabel('Revenue ($M)')
  .data(data, 'quarter', 'revenue')
  .build()

chart.show()
```

**Grouped bar chart:**
```groovy
def data = Matrix.builder()
  .data([
    ['Q1', 100, 90],
    ['Q2', 120, 110],
    ['Q3', 110, 105],
    ['Q4', 140, 130]
  ])
  .columnNames('quarter', 'revenue_2023', 'revenue_2024')
  .build()

def chart = BarChart.create()
  .title('Revenue Comparison')
  .grouped()  // Enable grouped bars
  .series('2023', data, 'quarter', 'revenue_2023')
  .series('2024', data, 'quarter', 'revenue_2024')
  .build()

chart.show()
```

### Pie Charts

```groovy
import se.alipsa.groovy.matrix.*
import se.alipsa.groovy.charts.*

def data = Matrix.builder()
  .data([
    ['Product A', 35],
    ['Product B', 25],
    ['Product C', 20],
    ['Product D', 15],
    ['Product E', 5]
  ])
  .columnNames('product', 'market_share')
  .build()

def chart = PieChart.create()
  .title('Market Share by Product')
  .data(data, 'product', 'market_share')
  .showLegend(true)
  .build()

chart.show()
```

### Scatter Plots

```groovy
import se.alipsa.groovy.matrix.*
import se.alipsa.groovy.charts.*

// Generate sample data
def data = Matrix.builder()
  .data((1..50).collect {
    [it, it * 2 + (Math.random() * 20 - 10)]
  })
  .columnNames('x', 'y')
  .build()

def chart = ScatterPlot.create()
  .title('Correlation Analysis')
  .xAxisLabel('Independent Variable')
  .yAxisLabel('Dependent Variable')
  .data(data, 'x', 'y')
  .showTrendLine(true)  // Add regression line
  .build()

chart.show()

// Print correlation coefficient
println "Correlation: ${data.correlation('x', 'y')}"
```

### Histograms

```groovy
import se.alipsa.groovy.matrix.*
import se.alipsa.groovy.charts.*

// Sample data: test scores
def scores = (1..100).collect {
  50 + (Math.random() - 0.5) * 40  // Normal distribution around 50
}

def data = Matrix.builder()
  .column('score', scores as Double[])
  .build()

def chart = Histogram.create()
  .title('Test Score Distribution')
  .xAxisLabel('Score')
  .yAxisLabel('Frequency')
  .data(data, 'score')
  .bins(10)  // Number of bins
  .build()

chart.show()

// Print statistics
println "Mean: ${data.mean('score')}"
println "Std Dev: ${data.stdDev('score')}"
```

### Box Plots

```groovy
import se.alipsa.groovy.matrix.*
import se.alipsa.groovy.charts.*

def data = Matrix.builder()
  .data([
    ['Group A', 10], ['Group A', 15], ['Group A', 20], ['Group A', 18],
    ['Group B', 25], ['Group B', 30], ['Group B', 28], ['Group B', 32],
    ['Group C', 15], ['Group C', 18], ['Group C', 22], ['Group C', 19]
  ])
  .columnNames('group', 'value')
  .build()

def chart = BoxPlot.create()
  .title('Value Distribution by Group')
  .xAxisLabel('Group')
  .yAxisLabel('Value')
  .data(data, 'group', 'value')
  .build()

chart.show()
```

![Box Plot](https://raw.githubusercontent.com/perNyfelt/gade/master/docs/boxplot_Temp_Month.png)

### Heatmaps

```groovy
import se.alipsa.groovy.matrix.*
import se.alipsa.groovy.charts.*

// Create correlation matrix
def data = Matrix.builder()
  .data([
    [1, 2, 3], [4, 5, 6], [7, 8, 9],
    [2, 3, 4], [5, 6, 7], [8, 9, 10]
  ])
  .columnNames('var1', 'var2', 'var3')
  .build()

def corrMatrix = data.correlation()

def chart = Heatmap.create()
  .title('Correlation Matrix')
  .data(corrMatrix)
  .colorScheme('RdYlGn')  // Red-Yellow-Green
  .build()

chart.show()
```

![Heatmap](https://raw.githubusercontent.com/perNyfelt/gade/master/docs/heatmap_Temp_Ozone.png)

### Customizing Charts

#### Colors and Themes

```groovy
def chart = LineChart.create()
  .title('Custom Styled Chart')
  .data(data, 'x', 'y')
  .color('#FF5733')  // Hex color
  .lineWidth(3)
  .markerSize(8)
  .theme('dark')  // dark, light, minimal
  .build()
```

#### Size and Layout

```groovy
// Set chart size
chart.setSize(1200, 800)

// Adjust margins
chart.setMargins(top: 50, right: 50, bottom: 70, left: 70)

// Grid lines
chart.showGrid(true)
chart.gridColor('#CCCCCC')
```

#### Exporting Charts

```groovy
// Save as PNG
chart.save(new File('chart.png'), 1920, 1080)

// Save as SVG (vector format)
chart.saveSVG(new File('chart.svg'))

// Save as PDF
chart.savePDF(new File('chart.pdf'))
```

### Interactive Visualizations

For interactive HTML charts using Plotly:

```groovy
import tech.tablesaw.api.*
import tech.tablesaw.plotly.Plot
import tech.tablesaw.plotly.api.*

// Create Tablesaw table
def table = Table.create('Sales')
  .addColumns(
    IntColumn.create('Month', 1..12 as int[]),
    DoubleColumn.create('Sales', [100, 120, 115, 140, 135, 150, 145, 160, 175, 180, 190, 200] as double[])
  )

// Create interactive line plot
def plot = LinePlot.create(
  'Monthly Sales',
  table,
  'Month',
  'Sales'
)

// Display in browser
Plot.show(plot)

// Or save as HTML
new File('chart.html').text = plot.asJavascript()
```

---

## Keyboard Shortcuts

Master these shortcuts for efficient workflow:

### File Operations

| Shortcut | Action |
|----------|--------|
| **Ctrl+N** | New script |
| **Ctrl+O** | Open file |
| **Ctrl+S** | Save current file |
| **Ctrl+Shift+S** | Save as... |
| **Ctrl+W** | Close current tab |
| **Ctrl+Shift+W** | Close all tabs |
| **Ctrl+Q** | Quit Gade |

### Editing

| Shortcut | Action |
|----------|--------|
| **Ctrl+Z** | Undo |
| **Ctrl+Y** | Redo |
| **Ctrl+X** | Cut |
| **Ctrl+C** | Copy |
| **Ctrl+V** | Paste |
| **Ctrl+A** | Select all |
| **Ctrl+F** | Find |
| **Ctrl+H** | Find and replace |
| **Ctrl+G** | Go to line |
| **Ctrl+D** | Duplicate line |
| **Ctrl+Shift+D** | Delete line |
| **Ctrl+/** | Toggle line comment |
| **Ctrl+Shift+/** | Toggle block comment |

### Code Execution

| Shortcut | Action |
|----------|--------|
| **F5** | Run full script |
| **Ctrl+Enter** | Run current line or selection |
| **Shift+F5** | Stop execution |
| **F6** | Run in new console |
| **Ctrl+Shift+Enter** | Run and clear console first |

### Code Completion

| Shortcut | Action |
|----------|--------|
| **Ctrl+Space** | Trigger code completion |
| **↑/↓** | Navigate suggestions |
| **Enter** | Accept suggestion |
| **Esc** | Dismiss completion |
| **Tab** | Accept and move to next field (if applicable) |

### Navigation

| Shortcut | Action |
|----------|--------|
| **Ctrl+Tab** | Next tab |
| **Ctrl+Shift+Tab** | Previous tab |
| **Ctrl+1** | Focus on editor |
| **Ctrl+2** | Focus on console |
| **Ctrl+3** | Focus on environment |
| **Ctrl+4** | Focus on files |
| **Ctrl+B** | Toggle file browser |
| **Ctrl+E** | Toggle environment panel |

### Git Operations

| Shortcut | Action |
|----------|--------|
| **Ctrl+G, S** | Git status |
| **Ctrl+G, C** | Git commit |
| **Ctrl+G, P** | Git push |
| **Ctrl+G, U** | Git pull (update) |
| **Ctrl+G, L** | Git log |
| **Ctrl+G, B** | Git branches |
| **Ctrl+G, D** | Git diff |

**Note:** For Git shortcuts, press **Ctrl+G**, release, then press the second key.

### View

| Shortcut | Action |
|----------|--------|
| **Ctrl++** | Increase font size |
| **Ctrl+-** | Decrease font size |
| **Ctrl+0** | Reset font size |
| **F11** | Toggle fullscreen |
| **Ctrl+Shift+P** | Command palette |

### Search and Replace

| Shortcut | Action |
|----------|--------|
| **Ctrl+F** | Find in current file |
| **Ctrl+Shift+F** | Find in files (project-wide) |
| **Ctrl+H** | Replace in current file |
| **Ctrl+Shift+H** | Replace in files |
| **F3** | Find next |
| **Shift+F3** | Find previous |

### Debugging and Tools

| Shortcut | Action |
|----------|--------|
| **Ctrl+Shift+I** | Inspect variable (select variable first) |
| **Ctrl+Shift+C** | Clear console |
| **Ctrl+Shift+E** | Clear environment |
| **Alt+Enter** | Show quick actions |

---

## Advanced Topics

### Custom Code Completion Engines

Gade's completion system is extensible. You can create custom completion engines:

```groovy
import se.alipsa.gade.code.completion.*

class MyCompletionEngine implements CompletionEngine {

    @Override
    String getLanguage() {
        return "mylang"
    }

    @Override
    List<CompletionItem> complete(CompletionContext context) {
        def items = []

        // Add custom completions
        items << CompletionItem.builder()
            .label("myFunction")
            .kind(CompletionKind.FUNCTION)
            .insertText("myFunction()")
            .cursorOffset(-1)  // Place cursor before )
            .documentation("My custom function")
            .build()

        return items
    }
}

// Register engine
CompletionRegistry.getInstance().register(new MyCompletionEngine())
```

### Working with Large Datasets

For datasets that don't fit in memory:

```groovy
import se.alipsa.groovy.matrix.*

// Stream large CSV file
def file = new File('huge_dataset.csv')
def processedRows = 0

file.eachLine { line, lineNumber ->
    if (lineNumber == 1) return  // Skip header

    def values = line.split(',')
    // Process one row at a time

    processedRows++
    if (processedRows % 10000 == 0) {
        println "Processed ${processedRows} rows..."
    }
}

println "Total rows: ${processedRows}"
```

**Chunked processing:**
```groovy
// Process in chunks of 10000 rows
def chunkSize = 10000
def chunk = []

file.eachLine { line, lineNumber ->
    if (lineNumber == 1) return

    chunk << line.split(',')

    if (chunk.size() >= chunkSize) {
        // Process chunk
        def matrix = Matrix.builder()
            .data(chunk)
            .columnNames('col1', 'col2', 'col3')
            .build()

        // Do analysis on chunk
        processChunk(matrix)

        chunk.clear()
    }
}

// Process remaining rows
if (chunk.size() > 0) {
    processChunk(Matrix.builder().data(chunk).build())
}
```

### Parallel Processing

For CPU-intensive tasks:

```groovy
import groovyx.gpars.GParsPool

// Parallel map
GParsPool.withPool {
    def results = (1..1000).collectParallel { n ->
        // CPU-intensive calculation
        computeExpensiveFunction(n)
    }
    println results
}

// Parallel each
GParsPool.withPool {
    def data = loadLargeDataset()
    data.eachParallel { row ->
        processRow(row)
    }
}
```

### Creating Reusable Scripts

Structure scripts for reusability:

```groovy
// utils/DataCleaning.groovy
class DataCleaning {

    static Matrix removeOutliers(Matrix data, String column, double threshold = 3.0) {
        def mean = data.mean(column)
        def stdDev = data.stdDev(column)

        return data.where { row ->
            def value = row[column]
            def zScore = Math.abs((value - mean) / stdDev)
            zScore < threshold
        }
    }

    static Matrix handleMissing(Matrix data, String strategy = 'mean') {
        // Implementation
        return data
    }
}
```

**Use in other scripts:**
```groovy
// analysis.groovy
evaluate(new File('utils/DataCleaning.groovy'))

def data = Matrix.create(new File('data.csv'))
data = DataCleaning.removeOutliers(data, 'price')
data = DataCleaning.handleMissing(data)
```

### Performance Optimization Tips

1. **Use appropriate data structures:**
   ```groovy
   // Fast: Matrix for tabular data
   def matrix = Matrix.create(file)

   // Slow: List of maps
   def data = []
   file.eachLine { data << parseToMap(it) }
   ```

2. **Minimize object creation in loops:**
   ```groovy
   // Good
   def result = new StringBuilder()
   (1..1000).each { result.append(it) }

   // Bad
   def result = ''
   (1..1000).each { result += it }  // Creates 1000 String objects
   ```

3. **Use @CompileStatic for performance-critical code:**
   ```groovy
   import groovy.transform.CompileStatic

   @CompileStatic
   double calculateSum(List<Double> numbers) {
       double sum = 0
       for (double n : numbers) {
           sum += n
       }
       return sum
   }
   ```

---

## Getting More Help

- **User Manual:** Help → User Manual (built-in)
- **Examples:** [GitHub examples directory](https://github.com/perNyfelt/gade/tree/main/examples)
- **Cookbook:** [docs/cookbook/cookbook.md](cookbook/cookbook.md)
- **Wiki:** [GitHub Wiki](https://github.com/perNyfelt/gade/wiki)
- **Issues:** [Report bugs or request features](https://github.com/perNyfelt/gade/issues)

---

**Last Updated:** February 3, 2026
**Version:** 1.0.0
