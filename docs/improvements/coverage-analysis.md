# Test Coverage Analysis

**Generated:** February 3, 2026
**Sprint:** 4 (Testing + Documentation)
**Overall Coverage:** 15% instruction coverage

## Summary

Current test coverage is **15%** (11,442 of 73,888 instructions covered).
Target for core components: **70% coverage**.

## Core Component Coverage

### Current State

| Component | Package | Coverage | Status | Gap to 70% |
|-----------|---------|----------|--------|-----------|
| **RuntimeProcessRunner** | `se.alipsa.gade.runtime` | 35% | 🟡 Needs work | +35% |
| **ConsoleComponent** | `se.alipsa.gade.console` | 4% | 🔴 Critical | +66% |
| **GroovyTypeResolver** | `se.alipsa.gade.code.completion.groovy` | 75% | ✅ Excellent | Met target |
| **ConnectionHandler** | `se.alipsa.gade.environment.connections` | 1% | 🔴 Critical | +69% |
| **CompletionEngine** | `se.alipsa.gade.code.completion` | 59% | 🟡 Close | +11% |
| **GradleUtils** | `se.alipsa.gade.utils.gradle` | 44% | 🟡 Needs work | +26% |
| **SQL Completion** | `se.alipsa.gade.code.completion.sql` | 72% | ✅ Excellent | Met target |
| **JavaScript Completion** | `se.alipsa.gade.code.completion.javascript` | 97% | ✅ Excellent | Met target |

### Package-Level Coverage

| Package | Instructions | Branches | Lines | Methods | Classes |
|---------|-------------|----------|-------|---------|---------|
| **se.alipsa.gade.runtime** | 35% | 28% | 917 lines | 142 methods | 14 classes |
| **se.alipsa.gade.console** | 4% | 0% | 943 lines | 192 methods | 23 classes |
| **se.alipsa.gade.code.completion** | 59% | 34% | 330 lines | 86 methods | 11 classes |
| **se.alipsa.gade.code.completion.groovy** | 75% | 51% | 669 lines | 53 methods | 5 classes |
| **se.alipsa.gade.code.completion.sql** | 72% | 53% | 105 lines | 14 methods | 4 classes |
| **se.alipsa.gade.code.completion.javascript** | 97% | 75% | 11 lines | 4 methods | 1 class |
| **se.alipsa.gade.utils.gradle** | 44% | 34% | 639 lines | 93 methods | 9 classes |
| **se.alipsa.gade.environment.connections** | 1% | 6% | 1154 lines | 151 methods | 14 classes |
| **se.alipsa.gade.code.groovytab** | 51% | 32% | 434 lines | 38 methods | 5 classes |
| **se.alipsa.gade.utils** | 26% | 22% | 1476 lines | 245 methods | 38 classes |

## Components Already Meeting 70% Target ✅

1. **GroovyTypeResolver** (75%) - Excellent coverage
2. **SQL Completion Engine** (72%) - Good coverage
3. **JavaScript Completion Engine** (97%) - Excellent coverage

## Priority Areas for Improvement

### Priority 1: Critical (< 10% coverage)

**ConnectionHandler (1% coverage)**
- Status: Just added 12 unit tests
- Issue: Tests only cover business logic, not database operations
- Reason: Database operations require full Gade infrastructure
- Action: Document that integration tests cover remaining functionality

### Priority 2: High (< 30% coverage)

**ConsoleComponent (4% coverage)**
- 943 lines, 192 methods
- Core functionality for script execution
- Requires JavaFX and runtime initialization
- Recommendation: Add focused tests for:
  - Script validation logic
  - Environment variable handling
  - Output formatting
  - Error message construction

### Priority 3: Medium (30-60% coverage)

**RuntimeProcessRunner (35% coverage)**
- Already has some tests (RuntimeProcessRunnerTest)
- Gap: +35% to reach 70%
- Focus areas:
  - JSON protocol handling
  - Error recovery
  - Process lifecycle management

**GradleUtils (44% coverage)**
- Well-tested in integration tests
- Gap: +26% to reach 70%
- Focus areas:
  - Cache hit/miss scenarios
  - Version fallback logic
  - Daemon recovery edge cases

**CompletionEngine (59% coverage)**
- Gap: +11% to reach 70%
- Focus areas:
  - Engine registration/lookup
  - Context switching
  - Cache invalidation

**GroovyTab (51% coverage)**
- Has existing tests (GroovyTabRunGroovyTest)
- Gap: +19% to reach 70%

## Challenges

### Infrastructure Requirements

Many core components require complex setup:

1. **JavaFX Platform** - UI components need JavaFX initialization
2. **Gade Instance** - Many components depend on `Gade.instance()`
3. **Dynamic ClassLoading** - GroovyClassLoader, dependency resolution
4. **Gradle Tooling API** - Full Gradle environment for integration
5. **Process Management** - Subprocess communication

### Testing Strategy

**Unit Tests** (Fast, isolated):
- Business logic
- URL parsing
- Configuration validation
- Data transformation

**Integration Tests** (Slower, realistic):
- Database connections
- Gradle/Maven integration
- Script execution
- Runtime switching

**Manual/Production Testing**:
- Full UI workflows
- Real-world scenarios
- Performance characteristics

## Recommendations

### Option 1: Pragmatic 70% Target

Accept that some components (ConsoleComponent, ConnectionHandler) are primarily integration-tested:

1. **Focus on testable core logic:**
   - RuntimeProcessRunner: JSON protocol, error handling
   - CompletionEngine: Registry, cache, context switching
   - GradleUtils: Cache logic, version handling

2. **Document infrastructure limitations:**
   - Mark components as "integration-test-covered"
   - Maintain existing integration test suite
   - Expand manual test scenarios

3. **Achieve 70% for:**
   - GroovyTypeResolver ✅ (already 75%)
   - SQL Completion ✅ (already 72%)
   - JavaScript Completion ✅ (already 97%)
   - CompletionEngine (59% → 70%) +11%
   - RuntimeProcessRunner (35% → 70%) +35%
   - GradleUtils (44% → 70%) +26%

**Estimated effort:** 20-30 hours

### Option 2: Aggressive Infrastructure Setup

Create test harness with full Gade initialization:

1. **Build comprehensive test infrastructure:**
   - JavaFX test harness
   - Mocked Gade environment
   - Gradle test fixtures
   - Process management utilities

2. **Achieve 70% for all components:**
   - All components in Option 1
   - ConsoleComponent (4% → 70%) +66%
   - ConnectionHandler (1% → 70%) +69%

**Estimated effort:** 60-80 hours

## Recommended Path Forward

**Adopt Option 1** with focused improvements:

### Phase 1: Low-Hanging Fruit (8-10 hours)
1. **CompletionEngine** - Add 11% coverage:
   - Test engine registration
   - Test cache invalidation
   - Test context switching
   - **Effort:** 3-4 hours

2. **GradleUtils** - Add 26% coverage:
   - Test cache scenarios
   - Test version fallback
   - Test daemon recovery
   - **Effort:** 5-6 hours

### Phase 2: RuntimeProcessRunner (12-15 hours)
3. **RuntimeProcessRunner** - Add 35% coverage:
   - Test JSON protocol parsing
   - Test error recovery
   - Test process lifecycle
   - **Effort:** 12-15 hours

### Phase 3: Documentation (2-3 hours)
4. **Document testing strategy:**
   - Update roadmap with coverage achieved
   - Document what's integration-tested
   - Create testing guidelines
   - **Effort:** 2-3 hours

**Total Estimated Effort:** 22-28 hours

### Acceptance Criteria

Core components at 70% or documented why not:

✅ GroovyTypeResolver: 75% (already met)
✅ SQL Completion: 72% (already met)
✅ JavaScript Completion: 97% (already met)
🎯 CompletionEngine: 59% → 70% (add +11%)
🎯 GradleUtils: 44% → 70% (add +26%)
🎯 RuntimeProcessRunner: 35% → 70% (add +35%)
📝 ConnectionHandler: 1% (document integration testing)
📝 ConsoleComponent: 4% (document integration testing)

## Notes

- Current total: **117 tests**, 15% overall coverage
- Target: **70% for core components** (not overall project)
- Strategy: Unit test what's feasible, document what requires integration testing
- Priority: Quality over quantity - well-designed tests that catch regressions

---

**Next Step:** Begin Phase 1 with CompletionEngine tests (fastest path to improvement)
